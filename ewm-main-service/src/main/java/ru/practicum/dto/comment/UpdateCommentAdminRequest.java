package ru.practicum.dto.comment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCommentAdminRequest {

    @NotNull
    private AdminCommentAction action;

    @Size(max = 1000)
    private String rejectionReason;
}
