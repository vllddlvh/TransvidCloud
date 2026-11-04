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
    projection_type = "ALL"
  }
}

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
}
