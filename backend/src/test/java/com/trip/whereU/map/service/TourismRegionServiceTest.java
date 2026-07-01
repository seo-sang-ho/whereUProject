package com.trip.whereU.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.trip.whereU.staystrength.repository.TourismStayStrengthRegionProjection;
import com.trip.whereU.staystrength.repository.TourismStayStrengthRepository;
import com.trip.whereU.map.client.NaverGeocodingClient;
import com.trip.whereU.map.dto.RegionCoordinate;
import com.trip.whereU.map.dto.TourismRegionSyncResponse;
import com.trip.whereU.map.entity.TourismRegion;
import com.trip.whereU.map.repository.TourismRegionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TourismRegionServiceTest {

	@Mock
	private TourismStayStrengthRepository tourismStayStrengthRepository;

	@Mock
	private TourismRegionRepository tourismRegionRepository;

	@Mock
	private NaverGeocodingClient naverGeocodingClient;

	@Test
	void geocodesOnlyMissingRegionsAndReportsFailures() {
		TourismRegionService service = new TourismRegionService(
				tourismStayStrengthRepository,
				tourismRegionRepository,
				naverGeocodingClient
		);
		TourismStayStrengthRegionProjection existing = region("11-11110", "서울특별시 종로구");
		TourismStayStrengthRegionProjection success = region("11-11140", "서울특별시 중구");
		TourismStayStrengthRegionProjection failure = region("11-11170", "서울특별시 용산구");
		when(tourismStayStrengthRepository.findDistinctRegions()).thenReturn(List.of(existing, success, failure));
		when(tourismRegionRepository.findAllById(anyList())).thenReturn(List.of(
				new TourismRegion("11-11110", "서울특별시 종로구", 37.57, 126.98)
		));
		when(naverGeocodingClient.geocode("서울특별시 중구"))
				.thenReturn(Optional.of(new RegionCoordinate(37.56, 126.99)));
		when(naverGeocodingClient.geocode("서울특별시 용산구")).thenReturn(Optional.empty());

		TourismRegionSyncResponse response = service.syncMissingCoordinates();

		ArgumentCaptor<List<TourismRegion>> captor = ArgumentCaptor.forClass(List.class);
		verify(tourismRegionRepository).saveAll(captor.capture());
		verify(naverGeocodingClient, never()).geocode("서울특별시 종로구");
		assertThat(captor.getValue()).extracting(TourismRegion::getRegionCode)
				.containsExactly("11-11140");
		assertThat(response.targetCount()).isEqualTo(3);
		assertThat(response.savedCount()).isEqualTo(1);
		assertThat(response.skippedCount()).isEqualTo(1);
		assertThat(response.failedRegionCodes()).containsExactly("11-11170");
	}

	private TourismStayStrengthRegionProjection region(String regionCode, String regionName) {
		return new TourismStayStrengthRegionProjection() {
			@Override
			public String getRegionCode() {
				return regionCode;
			}

			@Override
			public String getRegionName() {
				return regionName;
			}
		};
	}
}
