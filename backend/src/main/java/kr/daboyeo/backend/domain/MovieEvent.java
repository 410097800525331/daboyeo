package kr.daboyeo.backend.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "movie_events",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_movie_events_event_id_cinema", columnNames = {"event_id", "cinema"})
    }
)
public class MovieEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(nullable = false, length = 50)
    private String cinema;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(nullable = false)
    private String title;

    @Column
    private String imageUrl;


    @Column
    private LocalDate startDate;

    @Column
    private LocalDate endDate;

    @Column(length = 500)
    private String eventUrl;

    @Column
    private String dDay;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    // Constructors
    public MovieEvent() {}

    public MovieEvent(String source, Category category, String title, String imageUrl,
                     LocalDate startDate, LocalDate endDate, String dDay) {
        this(source, source, title, category, title, imageUrl, startDate, endDate, dDay);
    }

    public MovieEvent(String source, String cinema, Category category, String title, String imageUrl,
                     LocalDate startDate, LocalDate endDate, String dDay) {
        this(source, cinema, title, category, title, imageUrl, startDate, endDate, dDay);
    }

    public MovieEvent(String source, String cinema, String eventId, Category category, String title, String imageUrl,
                     LocalDate startDate, LocalDate endDate, String dDay) {
        this(source, cinema, eventId, category, title, imageUrl, startDate, endDate, dDay, null);
    }

    public MovieEvent(String source, String cinema, String eventId, Category category, String title, String imageUrl,
                     LocalDate startDate, LocalDate endDate, String dDay, String eventUrl) {
        this.source = source;
        this.cinema = cinema;
        this.eventId = eventId;
        this.category = category;
        this.title = title;
        this.imageUrl = imageUrl;
        this.startDate = startDate;
        this.endDate = endDate;
        this.dDay = dDay;
        this.eventUrl = eventUrl;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getCinema() {
        return cinema;
    }

    public void setCinema(String cinema) {
        this.cinema = cinema;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getdDay() {
        return dDay;
    }

    public void setdDay(String dDay) {
        this.dDay = dDay;
    }

    public String getEventUrl() {
        return eventUrl;
    }

    public void setEventUrl(String eventUrl) {
        this.eventUrl = eventUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
