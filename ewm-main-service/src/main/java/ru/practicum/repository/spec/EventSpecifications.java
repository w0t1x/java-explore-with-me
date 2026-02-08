package ru.practicum.repository.spec;

import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import ru.practicum.model.Event;
import ru.practicum.model.EventState;
import ru.practicum.model.ParticipationRequest;
import ru.practicum.model.RequestStatus;

import java.time.LocalDateTime;
import java.util.List;

public final class EventSpecifications {
    private EventSpecifications() {}

    public static Specification<Event> isPublished() {
        return (root, query, cb) -> cb.equal(root.get("state"), EventState.PUBLISHED);
    }

    public static Specification<Event> textSearch(String text) {
        if (text == null || text.isBlank()) return null;
        String pattern = "%" + text.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("annotation")), pattern),
                cb.like(cb.lower(root.get("description")), pattern)
        );
    }

    public static Specification<Event> categoryIn(List<Long> categories) {
        if (categories == null || categories.isEmpty()) return null;
        return (root, query, cb) -> root.get("category").get("id").in(categories);
    }

    public static Specification<Event> initiatorIn(List<Long> users) {
        if (users == null || users.isEmpty()) return null;
        return (root, query, cb) -> root.get("initiator").get("id").in(users);
    }

    public static Specification<Event> stateIn(List<EventState> states) {
        if (states == null || states.isEmpty()) return null;
        return (root, query, cb) -> root.get("state").in(states);
    }

    public static Specification<Event> paidEq(Boolean paid) {
        if (paid == null) return null;
        return (root, query, cb) -> cb.equal(root.get("paid"), paid);
    }

    public static Specification<Event> dateAfter(LocalDateTime start) {
        if (start == null) return null;
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("eventDate"), start);
    }

    public static Specification<Event> dateBefore(LocalDateTime end) {
        if (end == null) return null;
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("eventDate"), end);
    }

    public static Specification<Event> onlyAvailable(boolean onlyAvailable) {
        if (!onlyAvailable) return null;

        return (root, query, cb) -> {
            // participantLimit == 0 OR participantLimit > count(confirmed requests)
            Subquery<Long> sub = query.subquery(Long.class);
            Root<ParticipationRequest> req = sub.from(ParticipationRequest.class);
            sub.select(cb.count(req.get("id")));
            sub.where(
                    cb.equal(req.get("event"), root),
                    cb.equal(req.get("status"), RequestStatus.CONFIRMED)
            );

            return cb.or(
                    cb.equal(root.get("participantLimit"), 0),
                    cb.greaterThan(root.get("participantLimit"), sub)
            );
        };
    }
}
