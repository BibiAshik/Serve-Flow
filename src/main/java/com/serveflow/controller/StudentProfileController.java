package com.serveflow.controller;

import com.serveflow.entity.FoodItem;
import com.serveflow.entity.Student;
import com.serveflow.repository.FoodItemRepository;
import com.serveflow.repository.StudentRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/student")
@PreAuthorize("hasRole('STUDENT')")
public class StudentProfileController {

    private final StudentRepository studentRepository;
    private final FoodItemRepository foodItemRepository;

    public StudentProfileController(StudentRepository studentRepository, FoodItemRepository foodItemRepository) {
        this.studentRepository = studentRepository;
        this.foodItemRepository = foodItemRepository;
    }

    @GetMapping("/profile")
    public ResponseEntity<Student> getProfile(Authentication authentication) {
        String email = authentication.getName();
        Student student = studentRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("Student not found"));
        return ResponseEntity.ok(student);
    }

    @PutMapping("/profile")
    public ResponseEntity<Student> updateProfile(@RequestBody Map<String, String> updates, Authentication authentication) {
        String email = authentication.getName();
        Student student = studentRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("Student not found"));
        
        if (updates.containsKey("collegeId")) {
            student.setCollegeId(updates.get("collegeId"));
        }
        if (updates.containsKey("phone")) {
            student.setPhone(updates.get("phone"));
        }
        
        return ResponseEntity.ok(studentRepository.save(student));
    }

    @GetMapping("/favorites")
    public ResponseEntity<Set<FoodItem>> getFavorites(Authentication authentication) {
        String email = authentication.getName();
        Student student = studentRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("Student not found"));
        // Force initialization if lazy
        return ResponseEntity.ok(student.getFavoriteItems());
    }

    @PostMapping("/favorites/{foodId}")
    public ResponseEntity<String> addFavorite(@PathVariable Long foodId, Authentication authentication) {
        String email = authentication.getName();
        Student student = studentRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("Student not found"));
        FoodItem foodItem = foodItemRepository.findById(foodId).orElseThrow(() -> new RuntimeException("Food item not found"));
        
        student.getFavoriteItems().add(foodItem);
        studentRepository.save(student);
        
        return ResponseEntity.ok("Added to favorites");
    }

    @DeleteMapping("/favorites/{foodId}")
    public ResponseEntity<String> removeFavorite(@PathVariable Long foodId, Authentication authentication) {
        String email = authentication.getName();
        Student student = studentRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("Student not found"));
        FoodItem foodItem = foodItemRepository.findById(foodId).orElseThrow(() -> new RuntimeException("Food item not found"));
        
        student.getFavoriteItems().remove(foodItem);
        studentRepository.save(student);
        
        return ResponseEntity.ok("Removed from favorites");
    }
}
