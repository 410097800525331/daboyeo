package kr.daboyeo.backend.repository;

import kr.daboyeo.backend.domain.Category;
import kr.daboyeo.backend.domain.MovieEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MovieEventRepository extends JpaRepository<MovieEvent, Long> {

    // 중복 체크를 위한 메서드
    Optional<MovieEvent> findByTitleAndStartDateAndCategory(String title, LocalDate startDate, Category category);

    boolean existsByEventIdAndCinema(String eventId, String cinema);

    // 카테고리별 조회
    List<MovieEvent> findByCategory(Category category);

    // 소스별 조회
    List<MovieEvent> findBySource(String source);

    // 소스/카테고리 복합 조회
    List<MovieEvent> findBySourceAndCategory(String source, Category category);

    // 모든 이벤트 조회 (최신순)
    @Query("SELECT e FROM MovieEvent e ORDER BY e.createdAt DESC")
    List<MovieEvent> findAllOrderByCreatedAtDesc();

    // 특정 기간의 이벤트 조회
    @Query("SELECT e FROM MovieEvent e WHERE e.startDate <= :date AND (e.endDate IS NULL OR e.endDate >= :date)")
    List<MovieEvent> findActiveEventsOnDate(@Param("date") LocalDate date);
}
