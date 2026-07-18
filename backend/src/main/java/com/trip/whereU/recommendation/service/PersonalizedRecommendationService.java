package com.trip.whereU.recommendation.service;

import com.trip.whereU.map.entity.TourismRegion;
import com.trip.whereU.map.repository.TourismRegionRepository;
import com.trip.whereU.recommendation.dto.PersonalizedRecommendationItemResponse;
import com.trip.whereU.recommendation.dto.PersonalizedRecommendationResponse;
import com.trip.whereU.recommendation.dto.RecommendedTourismContentResponse;
import com.trip.whereU.recommendation.dto.ThemeScoreResponse;
import com.trip.whereU.resourcedemand.entity.TourismResourceDemand;
import com.trip.whereU.resourcedemand.entity.TourismTheme;
import com.trip.whereU.resourcedemand.repository.TourismResourceDemandRepository;
import com.trip.whereU.staystrength.entity.TourismStayStrength;
import com.trip.whereU.staystrength.repository.TourismStayStrengthRepository;
import com.trip.whereU.tourism.service.TourismContentBackfillService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalizedRecommendationService {

	private static final double THEME_DEMAND_WEIGHT = 0.7;
	private static final double LOW_STAY_STRENGTH_WEIGHT = 0.3;
	private static final int TOURISM_CONTENT_LIMIT_PER_REGION = 3;
	private static final int MAX_LIMIT = 100;

	private final TourismResourceDemandRepository resourceDemandRepository;
	private final TourismStayStrengthRepository stayStrengthRepository;
	private final TourismRegionRepository regionRepository;
	private final RecommendationTourismContentService recommendationTourismContentService;
	private final TourismContentBackfillService tourismContentBackfillService;

	public PersonalizedRecommendationService(
			TourismResourceDemandRepository resourceDemandRepository,
			TourismStayStrengthRepository stayStrengthRepository,
			TourismRegionRepository regionRepository,
			RecommendationTourismContentService recommendationTourismContentService,
			TourismContentBackfillService tourismContentBackfillService
	) {
		this.resourceDemandRepository = resourceDemandRepository;
		this.stayStrengthRepository = stayStrengthRepository;
		this.regionRepository = regionRepository;
		this.recommendationTourismContentService = recommendationTourismContentService;
		this.tourismContentBackfillService = tourismContentBackfillService;
	}

	@Transactional(readOnly = true)
	public PersonalizedRecommendationResponse getLatestRecommendations(
			List<TourismTheme> requestedThemes,
			int limit
	) {
		List<TourismTheme> themes = normalizeThemes(requestedThemes);
		validateLimit(limit);
		Optional<LocalDate> commonReferenceDate = findLatestCommonReferenceDate(themes);
		if (commonReferenceDate.isEmpty()) {
			return PersonalizedRecommendationResponse.empty(themes);
		}

		LocalDate referenceDate = commonReferenceDate.get();
		Map<String, TourismStayStrength> stayStrengthByRegion = stayStrengthRepository
				.findByReferenceDate(referenceDate)
				.stream()
				.collect(Collectors.toMap(TourismStayStrength::getRegionCode, Function.identity()));
		Map<String, TourismRegion> regionByCode = regionRepository.findAll().stream()
				.collect(Collectors.toMap(TourismRegion::getRegionCode, Function.identity()));

		Map<String, RegionThemeDemand> demandByRegion = aggregateThemeDemands(
				resourceDemandRepository.findByReferenceDateAndThemeIn(referenceDate, themes),
				themes
		);

		List<RecommendationCandidate> candidates = demandByRegion.values().stream()
				.filter(demand -> demand.hasEveryTheme(themes))
				.map(demand -> toCandidate(
						demand,
						stayStrengthByRegion.get(demand.regionCode()),
						regionByCode.get(demand.regionCode()),
						themes
				))
				.flatMap(Optional::stream)
				.sorted(Comparator
						.comparingDouble(RecommendationCandidate::score).reversed()
						.thenComparing(candidate -> candidate.demand().regionCode()))
				.limit(limit)
				.toList();
		List<String> regionCodes = candidates.stream()
				.map(candidate -> candidate.demand().regionCode())
				.toList();
		Map<String, List<RecommendedTourismContentResponse>> tourismContentsByRegion =
				recommendationTourismContentService.findContentsByRegionCodes(
						regionCodes,
						TOURISM_CONTENT_LIMIT_PER_REGION
				);
		tourismContentBackfillService.requestBackfillForMissingImages(regionCodes, tourismContentsByRegion);

		List<PersonalizedRecommendationItemResponse> recommendations = java.util.stream.IntStream
				.range(0, candidates.size())
				.mapToObj(index -> toResponse(
						index + 1,
						candidates.get(index),
						themes,
						tourismContentsByRegion
				))
				.toList();
		return PersonalizedRecommendationResponse.of(referenceDate, themes, recommendations);
	}

	private Optional<LocalDate> findLatestCommonReferenceDate(List<TourismTheme> themes) {
		Set<LocalDate> resourceDemandDates = new HashSet<>(
				resourceDemandRepository.findReferenceDatesByThemeDescending(themes.getFirst())
		);
		for (TourismTheme theme : themes.subList(1, themes.size())) {
			resourceDemandDates.retainAll(
					resourceDemandRepository.findReferenceDatesByThemeDescending(theme)
			);
		}
		return stayStrengthRepository.findReferenceDatesDescending().stream()
				.filter(resourceDemandDates::contains)
				.findFirst();
	}

	private Map<String, RegionThemeDemand> aggregateThemeDemands(
			List<TourismResourceDemand> demands,
			List<TourismTheme> themes
	) {
		Set<TourismTheme> selectedThemes = Set.copyOf(themes);
		Map<String, RegionThemeDemand> result = new LinkedHashMap<>();
		for (TourismResourceDemand demand : demands) {
			if (demand.getTheme() == null || !selectedThemes.contains(demand.getTheme())) {
				continue;
			}
			result.computeIfAbsent(
					demand.getRegionCode(),
					code -> new RegionThemeDemand(code, demand.getRegionName())
			).record(demand.getTheme(), demand.getNormalizedValue());
		}
		return result;
	}

	private Optional<RecommendationCandidate> toCandidate(
			RegionThemeDemand demand,
			TourismStayStrength stayStrength,
			TourismRegion region,
			List<TourismTheme> themes
	) {
		if (stayStrength == null) {
			return Optional.empty();
		}
		double themeDemandScore = demand.averageFor(themes);
		double score = themeDemandScore * THEME_DEMAND_WEIGHT
				+ (1 - stayStrength.getNormalizedStayStrength()) * LOW_STAY_STRENGTH_WEIGHT;
		return Optional.of(new RecommendationCandidate(demand, stayStrength, region, themeDemandScore, score));
	}

	private PersonalizedRecommendationItemResponse toResponse(
			int rank,
			RecommendationCandidate candidate,
			List<TourismTheme> themes,
			Map<String, List<RecommendedTourismContentResponse>> tourismContentsByRegion
	) {
		TourismRegion region = candidate.region();
		List<RecommendedTourismContentResponse> tourismContents = tourismContentsByRegion
				.getOrDefault(candidate.demand().regionCode(), List.of());
		return new PersonalizedRecommendationItemResponse(
				rank,
				candidate.demand().regionCode(),
				candidate.demand().regionName(),
				candidate.themeDemandScore(),
				candidate.stayStrength().getRawStayStrength(),
				candidate.stayStrength().getNormalizedStayStrength(),
				candidate.score(),
				(int) Math.round(candidate.score() * 100),
				interpret(candidate.score(), themes),
				candidate.demand().toThemeScores(themes),
				region == null ? null : region.getLatitude(),
				region == null ? null : region.getLongitude(),
				tourismContents
		);
	}

	private String interpret(double score, List<TourismTheme> themes) {
		String themeLabels = themes.stream().map(TourismTheme::getLabel).collect(Collectors.joining("·"));
		if (score >= 0.7) {
			return themeLabels + " 선호와 잘 맞고 체류 부담이 낮은 우선 추천 지역입니다.";
		}
		if (score >= 0.4) {
			return themeLabels + " 자원과 체류 여건이 비교적 균형 잡힌 지역입니다.";
		}
		return themeLabels + " 기준에서 다른 지역보다 추천 우선순위가 낮습니다.";
	}

	private List<TourismTheme> normalizeThemes(List<TourismTheme> requestedThemes) {
		if (requestedThemes == null || requestedThemes.isEmpty()) {
			throw new IllegalArgumentException("테마를 하나 이상 선택하세요.");
		}
		Set<TourismTheme> uniqueThemes = Set.copyOf(requestedThemes);
		return java.util.Arrays.stream(TourismTheme.values())
				.filter(uniqueThemes::contains)
				.toList();
	}

	private void validateLimit(int limit) {
		if (limit < 1 || limit > MAX_LIMIT) {
			throw new IllegalArgumentException("limit은 1 이상 100 이하로 입력하세요.");
		}
	}

	private static final class RegionThemeDemand {
		private final String regionCode;
		private final String regionName;
		private final Map<TourismTheme, DoubleSummaryStatistics> statistics = new EnumMap<>(TourismTheme.class);

		private RegionThemeDemand(String regionCode, String regionName) {
			this.regionCode = regionCode;
			this.regionName = regionName;
		}

		private void record(TourismTheme theme, double value) {
			statistics.computeIfAbsent(theme, ignored -> new DoubleSummaryStatistics()).accept(value);
		}

		private boolean hasEveryTheme(List<TourismTheme> themes) {
			return statistics.keySet().containsAll(themes);
		}

		private double averageFor(List<TourismTheme> themes) {
			return themes.stream().mapToDouble(theme -> statistics.get(theme).getAverage()).average().orElse(0);
		}

		private List<ThemeScoreResponse> toThemeScores(List<TourismTheme> themes) {
			List<ThemeScoreResponse> scores = new ArrayList<>();
			for (TourismTheme theme : themes) {
				DoubleSummaryStatistics themeStatistics = statistics.get(theme);
				scores.add(new ThemeScoreResponse(
						theme.name(),
						theme.getLabel(),
						themeStatistics.getAverage(),
						(int) themeStatistics.getCount()
				));
			}
			return List.copyOf(scores);
		}

		private String regionCode() {
			return regionCode;
		}

		private String regionName() {
			return regionName;
		}
	}

	private record RecommendationCandidate(
			RegionThemeDemand demand,
			TourismStayStrength stayStrength,
			TourismRegion region,
			double themeDemandScore,
			double score
	) {
	}
}
