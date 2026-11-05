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
    projection_type = "ALL"
  }
}

output "tables" {
  value = { for name, table in aws_dynamodb_table.this : name => { name = table.name, arn = table.arn } }
}
