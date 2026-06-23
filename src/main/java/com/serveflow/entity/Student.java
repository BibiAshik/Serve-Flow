package com.serveflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

/**
 * Student — represents a registered student using the Campus Bite system.
 * 
 * Created automatically upon first login via Google OAuth2.
 */
@Entity
@Table(name = "students")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Student {

    @Id
    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    private String collegeId;

    private String phone;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "student_favorites",
        joinColumns = @JoinColumn(name = "student_email"),
        inverseJoinColumns = @JoinColumn(name = "food_item_id")
    )
    private Set<FoodItem> favoriteItems = new HashSet<>();
}
