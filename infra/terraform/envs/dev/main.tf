module "network" {
  source      = "../../modules/network"
  environment = var.environment
}

module "storage" {
  source          = "../../modules/storage"
  environment     = var.environment
  account_id      = var.account_id
  allowed_origins = ["http://localhost:8000", "https://vbeeaistt.com", "https://${module.edge.domain}"]
}

module "queue" {
  source      = "../../modules/queue"
  environment = var.environment
}

module "database" {
  source      = "../../modules/database"
  environment = var.environment
}
