package com.trip.whereU.tourism.repository;

import com.trip.whereU.tourism.entity.TourismContent;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TourismContentRepository
		extends JpaRepository<TourismContent, Long>, JpaSpecificationExecutor<TourismContent> {

	Optional<TourismContent> findByContentId(String contentId);

	List<TourismContent> findByContentIdIn(Collection<String> contentIds);
}
