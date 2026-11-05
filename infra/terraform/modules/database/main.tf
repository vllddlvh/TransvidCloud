<<<<<<< HEAD
variable "prefix" { type = string }

locals {
  tables = {
    users    = { key = "userId", index = "email-index", index_key = "email", sort = null }
    projects = { key = "projectId", index = "owner-index", index_key = "ownerId", sort = "createdAt" }
    apikeys  = { key = "keyHash", index = "project-index", index_key = "projectId", sort = null }
    videos   = { key = "videoId", index = "project-index", index_key = "projectId", sort = "createdAt" }
  }
}

resource "aws_dynamodb_table" "this" {
  for_each     = local.tables
  name         = "${var.prefix}-${each.key}"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = each.value.key

  dynamic "attribute" {
    for_each = toset(compact([each.value.key, each.value.index_key, each.value.sort]))
    content {
      name = attribute.value
      type = "S"
    }
  }
  global_secondary_index {
    name            = each.value.index
    hash_key        = each.value.index_key
    range_key       = each.value.sort
=======
# 1. Bảng Users
resource "aws_dynamodb_table" "users" {
  name         = "tvc-${var.environment}-users"
  billing_mode = "PAY_PER_REQUEST" # On-Demand (0 request = 0 đồng)
  hash_key     = "userId"

  attribute {
    name = "userId"
    type = "S"
  }

  attribute {
    name = "email"
    type = "S"
  }

  global_secondary_index {
    name            = "email-index"
    hash_key        = "email"
>>>>>>> 6317d81836060cb47572c643c512292ce87cb101
    projection_type = "ALL"
  }
}

<<<<<<< HEAD
output "tables" {
  value = { for name, table in aws_dynamodb_table.this : name => { name = table.name, arn = table.arn } }
=======
# 2. Bảng Projects
resource "aws_dynamodb_table" "projects" {
  name         = "tvc-${var.environment}-projects"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "projectId"

  attribute {
    name = "projectId"
    type = "S"
  }

  attribute {
    name = "ownerId"
    type = "S"
  }

  attribute {
    name = "createdAt"
    type = "S"
  }

  global_secondary_index {
    name            = "owner-index"
    hash_key        = "ownerId"
    range_key       = "createdAt"
    projection_type = "ALL"
  }
}

# 3. Bảng ApiKeys
resource "aws_dynamodb_table" "apikeys" {
  name         = "tvc-${var.environment}-apikeys"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "keyHash"

  attribute {
    name = "keyHash"
    type = "S"
  }

  attribute {
    name = "projectId"
    type = "S"
  }

  global_secondary_index {
    name            = "project-index"
    hash_key        = "projectId"
    projection_type = "ALL"
  }
}

# 4. Bảng Videos
resource "aws_dynamodb_table" "videos" {
  name         = "tvc-${var.environment}-videos"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "videoId"

  attribute {
    name = "videoId"
    type = "S"
  }

  attribute {
    name = "projectId"
    type = "S"
  }

  attribute {
    name = "createdAt"
    type = "S"
  }

  global_secondary_index {
    name            = "project-index"
    hash_key        = "projectId"
    range_key       = "createdAt"
    projection_type = "ALL"
  }
>>>>>>> 6317d81836060cb47572c643c512292ce87cb101
}
