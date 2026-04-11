CREATE TABLE IF NOT EXISTS users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(100) NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS datasets (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    columns_json  TEXT NOT NULL,
    types_json    TEXT NOT NULL,
    target_column VARCHAR(100),
    task_type     VARCHAR(20),
    dataframe_json LONGTEXT,
    uploaded_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS training_jobs (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    dataset_id       BIGINT NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    model_type       VARCHAR(50) NOT NULL,
    hyperparams_json TEXT,
    eval_metric      DOUBLE,
    failure_reason   TEXT,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (dataset_id) REFERENCES datasets(id)
);

CREATE TABLE IF NOT EXISTS job_results (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id           BIGINT NOT NULL,
    worker_id        VARCHAR(100) NOT NULL,
    partition_idx    INT NOT NULL,
    predictions_json TEXT NOT NULL,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (job_id) REFERENCES training_jobs(id)
);
