CREATE TABLE `audit_events` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`user_id` text,
	`kind` text NOT NULL,
	`detail` text,
	`ip` text,
	`at` integer NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `audit_events_user` ON `audit_events` (`user_id`,`at`);--> statement-breakpoint
CREATE INDEX `audit_events_at` ON `audit_events` (`at`);--> statement-breakpoint
ALTER TABLE `connector_keys` ADD `expires_at` integer;--> statement-breakpoint
ALTER TABLE `users` ADD `session_version` integer DEFAULT 0 NOT NULL;--> statement-breakpoint
ALTER TABLE `users` ADD `totp_secret` text;--> statement-breakpoint
ALTER TABLE `users` ADD `totp_enabled_at` integer;--> statement-breakpoint
ALTER TABLE `users` ADD `totp_last_step` integer;