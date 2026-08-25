package com.projectos.backend.identity.user;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Removes recycle-bin accounts after their server-managed 30-day window. */
@Component
class UserRecycleBinPurgeJob {
    private static final Logger log = LoggerFactory.getLogger(UserRecycleBinPurgeJob.class);

    private final UserAccountRepository users;
    private final TransactionTemplate transactions;

    UserRecycleBinPurgeJob(UserAccountRepository users, TransactionTemplate transactions) {
        this.users = users;
        this.transactions = transactions;
    }

    @Scheduled(cron = "${app.identity.recycle-bin-purge-cron:0 0 3 * * *}")
    void purgeExpiredUsers() {
        users.findAllByStatusAndDeleteExpiresAtBefore(UserAccount.Status.DELETED, Instant.now())
                .stream()
                .map(UserAccount::getId)
                .forEach(this::purgeOne);
    }

    private void purgeOne(UUID id) {
        try {
            transactions.executeWithoutResult(status -> users.deleteById(id));
        } catch (DataIntegrityViolationException exception) {
            // Business records may still reference the account. Keep it in the recycle bin
            // until those references are resolved instead of deleting company history.
            log.info("Recycle-bin account {} retained because company records still reference it", id);
        }
    }
}
