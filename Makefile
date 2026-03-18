.PHONY: infra service processor worker up up-logs down

ROOT_DIR := $(CURDIR)

ENV_FILE ?= .env.local

infra:
	cd infra && docker compose up -d

service:
	cd event-service && ./gradlew bootRun

processor:
	cd notification-engine && ./gradlew bootRun

worker:
	set -a; [ -f $(ENV_FILE) ] && . ./$(ENV_FILE); set +a; cd push-worker && ./gradlew bootRun

up: infra
	@echo "Starting event-service..."
	cd event-service && ./gradlew bootRun &

	@echo "Starting notification-engine..."
	cd notification-engine && ./gradlew bootRun &

	@echo "Starting push-worker..."
	set -a; [ -f $(ENV_FILE) ] && . ./$(ENV_FILE); set +a; cd push-worker && ./gradlew bootRun &

up-logs: infra
	@echo "Opening event-service in a new Terminal window..."
	osascript -e 'tell application "Terminal" to activate' \
		-e 'tell application "Terminal" to do script "cd $(ROOT_DIR)/event-service && ./gradlew bootRun"'

	@echo "Opening notification-engine in a new Terminal window..."
	osascript -e 'tell application "Terminal" to activate' \
		-e 'tell application "Terminal" to do script "cd $(ROOT_DIR)/notification-engine && ./gradlew bootRun"'

	@echo "Opening push-worker in a new Terminal window..."
	osascript -e 'tell application "Terminal" to activate' \
		-e 'tell application "Terminal" to do script "set -a; [ -f $(ROOT_DIR)/$(ENV_FILE) ] && . $(ROOT_DIR)/$(ENV_FILE); set +a; cd $(ROOT_DIR)/push-worker && ./gradlew bootRun"'

down:
	cd infra && docker compose down

logs:
	cd infra && docker compose logs -f

kafka-topics:
	docker exec -it kafka kafka-topics --bootstrap-server localhost:9092 --list

redis-cli:
	docker exec -it redis redis-cli
