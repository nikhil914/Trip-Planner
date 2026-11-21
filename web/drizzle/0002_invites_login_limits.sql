CREATE TABLE `invites` (
	`token_hash` text PRIMARY KEY NOT NULL,
	`created_by` text NOT NULL,
	`note` text NOT NULL,
	`role` text DEFAULT 'USER' NOT NULL,
	`created_at` integer NOT NULL,
	`expires_at` integer NOT NULL,
	`used_at` integer,
	`used_by` text,
	FOREIGN KEY (`created_by`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`used_by`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE set null
);
--> statement-breakpoint
CREATE TABLE `login_failures` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`key` text NOT NULL,
	`at` integer NOT NULL
);
--> statement-breakpoint
CREATE INDEX `login_failures_key` ON `login_failures` (`key`,`at`);