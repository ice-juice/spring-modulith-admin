-- Notice recipient snapshots can exceed the framework's original 4000-character column.
ALTER TABLE EVENT_PUBLICATION MODIFY SERIALIZED_EVENT MEDIUMTEXT NOT NULL;
