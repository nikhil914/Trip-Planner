CREATE TABLE `expenses` (
	`trip_id` text NOT NULL,
	`id` text NOT NULL,
	`owner` text NOT NULL,
	`kind` text NOT NULL,
	`day_id` text,
	`block_id` text,
	`occurred_at` integer NOT NULL,
	`amount_minor` integer NOT NULL,
	`currency` text NOT NULL,
	`rate_to_home` real NOT NULL,
	`home_minor` integer NOT NULL,
	`billed_home_minor` integer,
	`method` text NOT NULL,
	`category` text NOT NULL,
	`merchant` text,
	`note` text,
	`prepaid` integer NOT NULL,
	`paid_by` text,
	`client_updated_at` integer NOT NULL,
	`deleted_at` integer,
	`rev` integer NOT NULL,
	`updated_at` integer NOT NULL,
	PRIMARY KEY(`trip_id`, `id`),
	FOREIGN KEY (`trip_id`) REFERENCES `trips`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `expenses_rev` ON `expenses` (`trip_id`,`rev`);--> statement-breakpoint
ALTER TABLE `trips` ADD `budget_minor` integer;--> statement-breakpoint
ALTER TABLE `trips` ADD `shared_ledger` integer DEFAULT false NOT NULL;