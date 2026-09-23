package com.afriland.audiences.notification.repository;

import com.afriland.audiences.notification.domain.NotificationAccuse;
import com.afriland.audiences.notification.domain.NotificationAccuse.TypeAccuse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationAccuseRepository extends JpaRepository<NotificationAccuse, Long> {

    /** Livraison au moins une fois : le même accusé peut arriver deux fois, il ne compte qu'une. */
    boolean existsByNotificationIdAndType(String notificationId, TypeAccuse type);
}
