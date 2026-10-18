package com.resolveit.grievancemanagement.controller;

import com.resolveit.grievancemanagement.dto.ComplaintRequest;
import com.resolveit.grievancemanagement.dto.ComplaintRatingRequest;
import com.resolveit.grievancemanagement.dto.UserComplaintResponse;
import com.resolveit.grievancemanagement.entity.Complaint;
import com.resolveit.grievancemanagement.service.ComplaintService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/complaints")
public class ComplaintController {

    private final ComplaintService complaintService;

    public ComplaintController(ComplaintService complaintService) {
        this.complaintService = complaintService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CITIZEN')")
    public ResponseEntity<Complaint> create(@Valid @RequestBody ComplaintRequest request) {
        return ResponseEntity.ok(complaintService.createComplaint(request));
    }

    @PostMapping("/anonymous")
    public ResponseEntity<Complaint> createAnonymous(@Valid @RequestBody ComplaintRequest request) {
        return ResponseEntity.ok(complaintService.createAnonymousComplaint(request));
    }

    @GetMapping
    @PreAuthorize("hasRole('CITIZEN')")
    public ResponseEntity<List<Complaint>> myComplaints() {
        return ResponseEntity.ok(complaintService.listMyComplaints());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CITIZEN')")
    public ResponseEntity<UserComplaintResponse> details(@PathVariable Long id) {
        return ResponseEntity.ok(complaintService.getMyComplaint(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CITIZEN')")
    public ResponseEntity<Complaint> update(@PathVariable Long id, @Valid @RequestBody ComplaintRequest request) {
        return ResponseEntity.ok(complaintService.updateMyComplaint(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CITIZEN')")
    public ResponseEntity<Void> withdraw(@PathVariable Long id) {
        complaintService.withdrawMyComplaint(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/rating")
    @PreAuthorize("hasRole('CITIZEN')")
    public ResponseEntity<UserComplaintResponse> rateOfficer(@PathVariable Long id,
                                                             @Valid @RequestBody ComplaintRatingRequest request) {
        return ResponseEntity.ok(complaintService.rateOfficer(id, request));
    }
}

