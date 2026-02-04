package ru.practicum.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.model.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // Метод 1: Используя встроенную функциональность Spring Data JPA
    Page<User> findByIdIn(List<Long> ids, Pageable pageable);

    // Метод 2: Кастомный запрос с пагинацией
    @Query("SELECT u FROM User u WHERE (:ids IS NULL OR u.id IN :ids)")
    Page<User> findUsersByIds(@Param("ids") List<Long> ids, Pageable pageable);

    // Метод 3: Кастомный запрос без пагинации (для сравнения)
    @Query("SELECT u FROM User u WHERE u.id IN :ids")
    List<User> findAllByIds(@Param("ids") List<Long> ids);
}
