output "roles" {
  value = { for name, role in aws_iam_role.runtime : name => { name = role.name, arn = role.arn } }
}
output "instance_profiles" {
  value = { for name, profile in aws_iam_instance_profile.runtime : name => { name = profile.name, arn = profile.arn } }
}
