ALTER TABLE bids
    DROP CONSTRAINT bids_user_id_fkey;

ALTER TABLE bids
    ADD CONSTRAINT fk_bids_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;
