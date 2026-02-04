package ru.practicum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.model.Event;
import ru.practicum.model.ParticipationRequest;

import java.util.List;
import java.util.Optional;

public interface ParticipationRequestRepository extends JpaRepository<ParticipationRequest, Long> {

    // Для пользователя
    List<ParticipationRequest> findByRequesterId(Long userId);

    List<ParticipationRequest> findByEventId(Long eventId);

    Optional<ParticipationRequest> findByEventIdAndRequesterId(Long eventId, Long userId);

    // Для администратора события
    @Query("SELECT pr FROM ParticipationRequest pr " +
            "WHERE pr.event.id = :eventId " +
            "AND pr.id IN :requestIds " +
            "AND pr.status = 'PENDING'")
    List<ParticipationRequest> findPendingRequests(@Param("eventId") Long eventId,
                                                   @Param("requestIds") List<Long> requestIds);

    List<ParticipationRequest> findByEventInAndStatus(List<Event> events,
                                                      ParticipationRequest.Status status);

    // Подсчет подтвержденных запросов
    @Query("SELECT COUNT(pr) FROM ParticipationRequest pr " +
            "WHERE pr.event.id = :eventId AND pr.status = 'CONFIRMED'")
    long countConfirmedRequests(@Param("eventId") Long eventId);

    // Проверка существования
    boolean existsByEventIdAndRequesterId(Long eventId, Long userId);

    // Получение по статусу
    List<ParticipationRequest> findByEventIdAndStatus(Long eventId, ParticipationRequest.Status status);

    @Query("SELECT pr FROM ParticipationRequest pr " +
            "WHERE pr.event.initiator.id = :userId " +
            "AND pr.event.id = :eventId")
    List<ParticipationRequest> findByEventInitiatorIdAndEventId(@Param("userId") Long userId,
                                                                @Param("eventId") Long eventId);
}