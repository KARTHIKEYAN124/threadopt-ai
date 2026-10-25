POLICIES = ["FIFO", "PRIORITY", "SJF"]
FEATURES = ["tasks", "durationMs", "io_ratio", "workers", "policy_code"]
def vector(tasks, duration, io_ratio, workers, policy):
    return [tasks, duration, io_ratio, workers, POLICIES.index(policy)]
