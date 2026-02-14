package ru.practicum.mapper;

import ru.practicum.dto.comment.CommentDto;
import ru.practicum.model.Comment;

public final class CommentMapper {
    private CommentMapper() {
    }

    public static CommentDto toDto(Comment entity) {
        return CommentDto.builder()
                .id(entity.getId())
                .text(entity.getText())
                .eventId(entity.getEvent().getId())
                .author(UserMapper.toShortDto(entity.getAuthor()))
                .status(entity.getStatus())
                .createdOn(entity.getCreatedOn())
                .updatedOn(entity.getUpdatedOn())
                .moderatedOn(entity.getModeratedOn())
                .rejectionReason(entity.getRejectionReason())
                .build();
    }
}
