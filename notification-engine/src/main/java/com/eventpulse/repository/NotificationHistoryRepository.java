package com.eventpulse.repository;

import com.eventpulse.entity.NotificationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, Long> {

    List<NotificationHistory> findTop20ByUserIdOrderByCreatedAtDesc(String userId);

    NotificationHistory findByNotificationId(String notificationId);
}
