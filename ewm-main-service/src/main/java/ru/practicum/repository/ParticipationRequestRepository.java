package ru.practicum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.model.ParticipationRequest;
import ru.practicum.model.RequestStatus;

import java.util.Collection;
import java.util.List;

public interface ParticipationRequestRepository extends JpaRepository<ParticipationRequest, Long> {

    List<ParticipationRequest> findAllByRequesterId(Long requesterId);

    boolean existsByRequesterIdAndEventId(Long requesterId, Long eventId);

    List<ParticipationRequest> findAllByEventId(Long eventId);

    List<ParticipationRequest> findAllByEventIdAndIdIn(Long eventId, Collection<Long> ids);

    long countByEventIdAndStatus(Long eventId, RequestStatus status);

    @Query("select r.event.id as eventId, count(r.id) as cnt " +
            "from ParticipationRequest r " +
            "where r.status = :status and r.event.id in :eventIds " +
            "group by r.event.id")
    List<ConfirmedCount> countByEventIdsAndStatus(@Param("eventIds") Collection<Long> eventIds,
                                                  @Param("status") RequestStatus status);
}
