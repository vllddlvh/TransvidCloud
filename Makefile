# Điểm vào duy nhất cho mọi lệnh. Logic phức tạp nằm trong scripts/ và local/scripts/.
# Lưu ý: Git Bash mặc định KHÔNG có make. Cài: `winget install GnuWin32.Make` hoặc `choco install make`.
SHELL := /bin/bash
COMPOSE := docker compose
.DEFAULT_GOAL := help

help: ## Liệt kê lệnh
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  %-14s %s\n",$$1,$$2}'

infra: ## Chỉ chạy LocalStack (dev backend/worker bằng IDE)
	$(COMPOSE) up -d localstack

up: ## Chạy toàn bộ stack local (LocalStack + backend + worker + nginx)
	$(COMPOSE) --profile full up -d --build

down: ## Dừng stack
	$(COMPOSE) --profile full down

reset: ## Xóa sạch dữ liệu local và dựng lại LocalStack
	$(COMPOSE) --profile full down -v
	$(MAKE) infra

logs: ## Xem log (make logs S=worker)
	$(COMPOSE) logs -f $(S)

worker-scale: ## Thử competing consumers: make worker-scale N=3
	$(COMPOSE) --profile full up -d --scale worker=$(N)

smoke: ## Kiểm tra LocalStack đã có đủ bucket/queue/bảng
	bash ./local/scripts/smoke-test.sh

chaos: ## Chaos worker (mặc định trong container: SIGTERM/SIGKILL/DLQ/competing)
	TVC_CHAOS_RUNTIME=$${TVC_CHAOS_RUNTIME:-docker} bash ./local/scripts/chaos-kill-worker.sh

test: ## Unit test các module đã có code
	@if [ -f backend/mvnw ]; then (cd backend && ./mvnw -q test); else echo "backend: chưa có code, bỏ qua"; fi
	@if [ -f worker/go.mod ]; then (cd worker && go test ./...); else echo "worker: chưa có code, bỏ qua"; fi
	@if [ -f frontend/package.json ]; then (cd frontend && npm test --if-present); else echo "frontend: chưa có code, bỏ qua"; fi

e2e: ## E2E qua API thật (A4; cần backend + worker đang chạy)
	bash ./tests/e2e/run.sh api

e2e-worker: ## E2E worker integration (LocalStack + image worker + playback /media)
	bash ./tests/e2e/run.sh worker

bench: ## Benchmark local 1 vs 2 worker (không thay thế M4 trên AWS)
	bash ./tests/bench/run.sh

tf-plan: ## terraform plan cho môi trường dev
	cd infra/terraform/envs/dev && terraform init && terraform plan

.PHONY: help infra up down reset logs worker-scale smoke chaos test e2e e2e-worker bench tf-plan
