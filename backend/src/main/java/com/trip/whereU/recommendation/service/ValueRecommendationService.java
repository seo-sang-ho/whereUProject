package com.trip.whereU.recommendation.service;

import com.trip.whereU.map.entity.TourismRegion;
import com.trip.whereU.map.repository.TourismRegionRepository;
import com.trip.whereU.recommendation.dto.ValueRecommendationItemResponse;
import com.trip.whereU.recommendation.dto.ValueRecommendationResponse;
import com.trip.whereU.servicedemand.entity.TourismServiceDemand;
import com.trip.whereU.servicedemand.repository.TourismServiceDemandRepository;
import com.trip.whereU.staystrength.entity.TourismStayStrength;
import com.trip.whereU.staystrength.repository.TourismStayStrengthRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ValueRecommendationService {

	private static final double SERVICE_DEMAND_WEIGHT = 0.7;
	private static final double LOW_STAY_STRENGTH_WEIGHT = 0.3;
	private static final int MAX_LIMIT = 100;

	private final TourismServiceDemandRepository serviceDemandRepository;
	private final TourismStayStrengthRepository stayStrengthRepository;
	private final TourismRegionRepository regionRepository;

	public ValueRecommendationService(
			TourismServiceDemandRepository serviceDemandRepository,
			TourismStayStrengthRepository stayStrengthRepository,
			TourismRegionRepository regionRepository
	) {
		this.serviceDemandRepository = serviceDemandRepository;
		this.stayStrengthRepository = stayStrengthRepository;
		this.regionRepository = regionRepository;
	}

	@Transactional(readOnly = true)
	public ValueRecommendationResponse getLatestValueRecommendations(int limit) {
		validateLimit(limit);
		Optional<LocalDate> commonReferenceDate = findLatestCommonReferenceDate();
		if (commonReferenceDate.isEmpty()) {
			return ValueRecommendationResponse.empty();
		}

		LocalDate referenceDate = commonReferenceDate.get();
		Map<String, TourismStayStrength> stayStrengthByRegion = stayStrengthRepository
				.findByReferenceDate(referenceDate)
				.stream()
				.collect(Collectors.toMap(TourismStayStrength::getRegionCode, Function.identity()));
		Map<String, TourismRegion> regionByCode = regionRepository.findAll().stream()
				.collect(Collectors.toMap(TourismRegion::getRegionCode, Function.identity()));

		List<RecommendationCandidate> candidates = serviceDemandRepository.findByReferenceDate(referenceDate)
				.stream()
				.map(serviceDemand -> toCandidate(
						serviceDemand,
						stayStrengthByRegion.get(serviceDemand.getRegionCode()),
						regionByCode.get(serviceDemand.getRegionCode())
				))
				.flatMap(Optional::stream)
				.sorted(Comparator
						.comparingDouble(RecommendationCandidate::score).reversed()
						.thenComparing(candidate -> candidate.serviceDemand().getRegionCode()))
				.limit(limit)
				.toList();

		List<ValueRecommendationItemResponse> recommendations = java.util.stream.IntStream
				.range(0, candidates.size())
				.mapToObj(index -> toResponse(index + 1, candidates.get(index)))
				.toList();
		return ValueRecommendationResponse.of(referenceDate, recommendations);
	}

	private Optional<LocalDate> findLatestCommonReferenceDate() {
		Set<LocalDate> serviceDemandDates = new HashSet<>(
				serviceDemandRepository.findReferenceDatesDescending()
		);
		return stayStrengthRepository.findReferenceDatesDescending().stream()
				.filter(serviceDemandDates::contains)
				.findFirst();
	}

	private Optional<RecommendationCandidate> toCandidate(
			TourismServiceDemand serviceDemand,
			TourismStayStrength stayStrength,
			TourismRegion region
	) {
		if (stayStrength == null) {
			return Optional.empty();
		}
		double score = serviceDemand.getNormalizedServiceDemand() * SERVICE_DEMAND_WEIGHT
				+ (1 - stayStrength.getNormalizedStayStrength()) * LOW_STAY_STRENGTH_WEIGHT;
		return Optional.of(new RecommendationCandidate(serviceDemand, stayStrength, region, score));
	}

	private ValueRecommendationItemResponse toResponse(int rank, RecommendationCandidate candidate) {
		TourismServiceDemand serviceDemand = candidate.serviceDemand();
		TourismStayStrength stayStrength = candidate.stayStrength();
		TourismRegion region = candidate.region();
		return new ValueRecommendationItemResponse(
				rank,
				serviceDemand.getRegionCode(),
				serviceDemand.getRegionName(),
				serviceDemand.getRawServiceDemand(),
				serviceDemand.getNormalizedServiceDemand(),
				stayStrength.getRawStayStrength(),
				stayStrength.getNormalizedStayStrength(),
				candidate.score(),
				(int) Math.round(candidate.score() * 100),
				interpret(candidate.score()),
				region == null ? null : region.getLatitude(),
				region == null ? null : region.getLongitude()
		);
	}

	private String interpret(double score) {
		if (score >= 0.7) {
			return "서비스 매력은 높고 체류 인파 부담은 낮은 우선 추천 지역입니다.";
		}
		if (score >= 0.4) {
			return "서비스 매력과 체류 여건이 비교적 균형 잡힌 지역입니다.";
		}
		return "현재 기준에서는 다른 지역보다 추천 우선순위가 낮습니다.";
	}

	private void validateLimit(int limit) {
		if (limit < 1 || limit > MAX_LIMIT) {
			throw new IllegalArgumentException("limit은 1 이상 100 이하로 입력하세요.");
		}
	}

	private record RecommendationCandidate(
			TourismServiceDemand serviceDemand,
			TourismStayStrength stayStrength,
			TourismRegion region,
			double score
	) {
	}
}
