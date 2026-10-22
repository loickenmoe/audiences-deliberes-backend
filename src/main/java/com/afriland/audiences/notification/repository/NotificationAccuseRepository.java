package com.afriland.audiences.notification.repository;

import com.afriland.audiences.notification.domain.NotificationAccuse;
import com.afriland.audiences.notification.domain.NotificationAccuse.TypeAccuse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationAccuseRepository extends JpaRepository<NotificationAccuse, Long> {

    /** At-least-once delivery: the same receipt may arrive twice but only counts once. */
    boolean existsByNotificationIdAndType(String notificationId, TypeAccuse type);
}
