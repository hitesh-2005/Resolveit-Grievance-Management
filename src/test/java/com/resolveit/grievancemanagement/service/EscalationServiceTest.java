package com.resolveit.grievancemanagement.service;

import com.resolveit.grievancemanagement.entity.Complaint;
import com.resolveit.grievancemanagement.entity.ComplaintStatusHistory;
import com.resolveit.grievancemanagement.entity.User;
import com.resolveit.grievancemanagement.repository.ComplaintRepository;
import com.resolveit.grievancemanagement.repository.ComplaintStatusHistoryRepository;
import com.resolveit.grievancemanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EscalationServiceTest {

    @Mock
    private ComplaintRepository complaintRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ComplaintStatusHistoryRepository statusHistoryRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private EscalationService escalationService;

    private User systemActor;
    private User l2Officer;
    private Complaint overdueComplaint;

    @BeforeEach
    void setUp() {
        systemActor = new User();
        systemActor.setId(999L);
        systemActor.setUsername(EscalationService.SYSTEM_ACTOR_USERNAME);
        systemActor.setEmail(EscalationService.SYSTEM_ACTOR_EMAIL);
        systemActor.setRole(User.Role.ADMIN);

        l2Officer = new User();
        l2Officer.setId(201L);
        l2Officer.setUsername("officer_l2_1");
        l2Officer.setRole(User.Role.OFFICER);
        l2Officer.setOfficerLevel(User.OfficerLevel.L2);

        overdueComplaint = new Complaint();
        overdueComplaint.setId(101L);
        overdueComplaint.setTitle("Overdue Civic Issue");
        overdueComplaint.setStatus(Complaint.Status.IN_PROGRESS);
        overdueComplaint.setCreatedAt(LocalDateTime.now().minusDays(5));
        overdueComplaint.setDueDate(LocalDateTime.now().minusDays(1));
    }

    @Test
    @DisplayName("Regression Test: Auto-escalation of overdue complaint records history with non-null System Actor")
    void runEscalationChecks_OverdueAssignedComplaint_EscalatesWithNonNullSystemActor() {
        // Given
        when(userRepository.findByRoleAndOfficerLevel(User.Role.OFFICER, User.OfficerLevel.L2))
                .thenReturn(List.of(l2Officer));
        when(userRepository.findByUsername(EscalationService.SYSTEM_ACTOR_USERNAME))
                .thenReturn(Optional.of(systemActor));
        when(complaintRepository.findUnassignedComplaintsForEscalation(any()))
                .thenReturn(Collections.emptyList());
        when(complaintRepository.findOverdueComplaintsForEscalation(eq(User.OfficerLevel.L1), any(LocalDateTime.class), any()))
                .thenReturn(List.of(overdueComplaint));

        // When
        escalationService.runEscalationChecks();

        // Then
        assertThat(overdueComplaint.getStatus())
                .as("Overdue complaint status should be updated to ESCALATED")
                .isEqualTo(Complaint.Status.ESCALATED);
        assertThat(overdueComplaint.getAssignedOfficer())
                .as("Overdue complaint should be reassigned to an L2 officer")
                .isEqualTo(l2Officer);

        verify(complaintRepository, times(1)).save(overdueComplaint);

        // Verify ComplaintStatusHistory was recorded and critically has a NON-NULL changedBy user
        ArgumentCaptor<ComplaintStatusHistory> historyCaptor = ArgumentCaptor.forClass(ComplaintStatusHistory.class);
        verify(statusHistoryRepository, times(1)).save(historyCaptor.capture());

        ComplaintStatusHistory savedHistory = historyCaptor.getValue();
        assertThat(savedHistory.getChangedBy())
                .as("Status history changedBy MUST NOT be null (satisfies database non-null constraint and audit trail)")
                .isNotNull();
        assertThat(savedHistory.getChangedBy().getUsername())
                .as("Automated escalation history should be attributed to the system daemon")
                .isEqualTo(EscalationService.SYSTEM_ACTOR_USERNAME);
        assertThat(savedHistory.getStatus())
                .isEqualTo(Complaint.Status.ESCALATED);
        assertThat(savedHistory.getComplaint())
                .isEqualTo(overdueComplaint);
    }

    @Test
    @DisplayName("Auto-escalation of unassigned complaint exceeding threshold records non-null history")
    void runEscalationChecks_UnassignedOverdueComplaint_EscalatesWithSystemActor() {
        // Given
        Complaint unassignedComplaint = new Complaint();
        unassignedComplaint.setId(102L);
        unassignedComplaint.setTitle("Unassigned Streetlight Repair");
        unassignedComplaint.setStatus(Complaint.Status.NEW);
        unassignedComplaint.setCreatedAt(LocalDateTime.now().minusHours(48));
        unassignedComplaint.setEscalationThresholdHours(24);

        when(userRepository.findByRoleAndOfficerLevel(User.Role.OFFICER, User.OfficerLevel.L2))
                .thenReturn(List.of(l2Officer));
        when(userRepository.findByUsername(EscalationService.SYSTEM_ACTOR_USERNAME))
                .thenReturn(Optional.of(systemActor));
        when(complaintRepository.findUnassignedComplaintsForEscalation(any()))
                .thenReturn(List.of(unassignedComplaint));
        when(complaintRepository.findOverdueComplaintsForEscalation(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        // When
        escalationService.runEscalationChecks();

        // Then
        assertThat(unassignedComplaint.getStatus()).isEqualTo(Complaint.Status.ESCALATED);
        assertThat(unassignedComplaint.getAssignedOfficer()).isEqualTo(l2Officer);

        ArgumentCaptor<ComplaintStatusHistory> historyCaptor = ArgumentCaptor.forClass(ComplaintStatusHistory.class);
        verify(statusHistoryRepository, times(1)).save(historyCaptor.capture());

        ComplaintStatusHistory savedHistory = historyCaptor.getValue();
        assertThat(savedHistory.getChangedBy()).isNotNull();
        assertThat(savedHistory.getChangedBy().getUsername()).isEqualTo(EscalationService.SYSTEM_ACTOR_USERNAME);
    }

    @Test
    @DisplayName("Manual escalation records history with specified user actor")
    void escalateComplaint_WithManualActor_RecordsHistoryWithGivenActor() {
        // Given
        User adminUser = new User();
        adminUser.setId(50L);
        adminUser.setUsername("admin_supervisor");
        adminUser.setRole(User.Role.ADMIN);

        Complaint complaint = new Complaint();
        complaint.setId(103L);
        complaint.setStatus(Complaint.Status.UNDER_REVIEW);

        when(complaintRepository.findById(103L)).thenReturn(Optional.of(complaint));
        when(userRepository.findById(201L)).thenReturn(Optional.of(l2Officer));

        // When
        escalationService.escalateComplaint(103L, 201L, adminUser);

        // Then
        assertThat(complaint.getStatus()).isEqualTo(Complaint.Status.ESCALATED);
        assertThat(complaint.getAssignedOfficer()).isEqualTo(l2Officer);

        ArgumentCaptor<ComplaintStatusHistory> historyCaptor = ArgumentCaptor.forClass(ComplaintStatusHistory.class);
        verify(statusHistoryRepository, times(1)).save(historyCaptor.capture());

        ComplaintStatusHistory savedHistory = historyCaptor.getValue();
        assertThat(savedHistory.getChangedBy()).isNotNull();
        assertThat(savedHistory.getChangedBy().getUsername()).isEqualTo("admin_supervisor");
    }
}
