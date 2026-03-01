#!/bin/bash

# Vessel Monitoring System - Docker Management Script
# This script helps manage Docker Compose services

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Project name
PROJECT_NAME="vms"

# Function to print colored messages
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to check if Docker is running
check_docker() {
    if ! docker info > /dev/null 2>&1; then
        print_error "Docker is not running. Please start Docker and try again."
        exit 1
    fi
    print_success "Docker is running"
}

# Function to start all services
start_all() {
    print_info "Starting all services..."
    docker-compose up -d
    print_success "All services started"
    show_status
}

# Function to start only infrastructure services (no apps)
start_infra() {
    print_info "Starting infrastructure services..."
    docker-compose up -d zookeeper kafka kafka-ui postgres clickhouse redis elasticsearch kibana
    print_success "Infrastructure services started"
    show_status
}

# Function to start only message broker
start_kafka() {
    print_info "Starting Kafka and Zookeeper..."
    docker-compose up -d zookeeper kafka kafka-ui kafka-init
    print_success "Kafka services started"
}

# Function to start only databases
start_databases() {
    print_info "Starting databases..."
    docker-compose up -d postgres clickhouse redis elasticsearch
    print_success "Database services started"
}

# Function to stop all services
stop_all() {
    print_info "Stopping all services..."
    docker-compose down
    print_success "All services stopped"
}

# Function to restart all services
restart_all() {
    print_info "Restarting all services..."
    docker-compose restart
    print_success "All services restarted"
}

# Function to show status
show_status() {
    print_info "Service status:"
    docker-compose ps
}

# Function to show logs
show_logs() {
    if [ -z "$1" ]; then
        print_info "Showing logs for all services (Ctrl+C to exit)..."
        docker-compose logs -f
    else
        print_info "Showing logs for $1 (Ctrl+C to exit)..."
        docker-compose logs -f "$1"
    fi
}

# Function to clean up (remove volumes)
cleanup() {
    print_warning "This will remove all containers, networks, and volumes. Data will be lost!"
    read -p "Are you sure? (yes/no): " -r
    echo
    if [[ $REPLY == "yes" ]]; then
        print_info "Cleaning up..."
        docker-compose down -v
        print_success "Cleanup complete"
    else
        print_info "Cleanup cancelled"
    fi
}

# Function to reset databases
reset_databases() {
    print_warning "This will reset all databases. Data will be lost!"
    read -p "Are you sure? (yes/no): " -r
    echo
    if [[ $REPLY == "yes" ]]; then
        print_info "Resetting databases..."
        docker-compose stop postgres clickhouse redis elasticsearch
        docker volume rm vms_postgres-data vms_clickhouse-data vms_redis-data vms_elasticsearch-data 2>/dev/null || true
        docker-compose up -d postgres clickhouse redis elasticsearch
        print_success "Databases reset"
    else
        print_info "Reset cancelled"
    fi
}

# Function to check health
check_health() {
    print_info "Checking service health..."
    echo ""
    
    # Kafka
    if curl -s http://localhost:8090 > /dev/null; then
        print_success "Kafka UI: ✓ http://localhost:8090"
    else
        print_error "Kafka UI: ✗ Not responding"
    fi
    
    # PostgreSQL
    if docker exec vms-postgres pg_isready -U postgres > /dev/null 2>&1; then
        print_success "PostgreSQL: ✓ localhost:5432"
    else
        print_error "PostgreSQL: ✗ Not ready"
    fi
    
    # ClickHouse
    if curl -s http://localhost:8123/ping > /dev/null; then
        print_success "ClickHouse: ✓ http://localhost:8123"
    else
        print_error "ClickHouse: ✗ Not responding"
    fi
    
    # Redis
    if docker exec vms-redis redis-cli ping > /dev/null 2>&1; then
        print_success "Redis: ✓ localhost:6379"
    else
        print_error "Redis: ✗ Not responding"
    fi
    
    # Elasticsearch
    if curl -s http://localhost:9200/_cluster/health > /dev/null; then
        print_success "Elasticsearch: ✓ http://localhost:9200"
    else
        print_error "Elasticsearch: ✗ Not responding"
    fi
    
    # Kibana
    if curl -s http://localhost:5601/api/status > /dev/null 2>&1; then
        print_success "Kibana: ✓ http://localhost:5601"
    else
        print_warning "Kibana: ⚠ Not ready yet (may still be starting)"
    fi
}

# Function to backup databases
backup_databases() {
    BACKUP_DIR="./backups/$(date +%Y%m%d_%H%M%S)"
    mkdir -p "$BACKUP_DIR"
    
    print_info "Backing up databases to $BACKUP_DIR..."
    
    # PostgreSQL backup
    print_info "Backing up PostgreSQL..."
    docker exec vms-postgres pg_dump -U postgres vessel_monitoring > "$BACKUP_DIR/postgres_backup.sql"
    print_success "PostgreSQL backup complete"
    
    # Redis backup
    print_info "Backing up Redis..."
    docker exec vms-redis redis-cli SAVE > /dev/null
    docker cp vms-redis:/data/dump.rdb "$BACKUP_DIR/redis_backup.rdb"
    print_success "Redis backup complete"
    
    print_success "All backups complete in $BACKUP_DIR"
}

# Function to show Kafka topics
show_kafka_topics() {
    print_info "Kafka topics:"
    docker exec vms-kafka kafka-topics --bootstrap-server localhost:9092 --list
}

# Function to describe Kafka topic
describe_kafka_topic() {
    if [ -z "$1" ]; then
        print_error "Please specify a topic name"
        exit 1
    fi
    print_info "Describing topic: $1"
    docker exec vms-kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic "$1"
}

# Function to query ClickHouse
query_clickhouse() {
    if [ -z "$1" ]; then
        print_error "Please specify a query"
        exit 1
    fi
    docker exec vms-clickhouse clickhouse-client --query "$1"
}

# Function to connect to PostgreSQL
psql_connect() {
    print_info "Connecting to PostgreSQL..."
    docker exec -it vms-postgres psql -U postgres -d vessel_monitoring
}

# Function to connect to Redis CLI
redis_cli() {
    print_info "Connecting to Redis CLI..."
    docker exec -it vms-redis redis-cli
}

# Function to show help
show_help() {
    echo "Vessel Monitoring System - Docker Management Script"
    echo ""
    echo "Usage: $0 [command]"
    echo ""
    echo "Commands:"
    echo "  start              Start all services"
    echo "  start-infra        Start only infrastructure services"
    echo "  start-kafka        Start only Kafka and Zookeeper"
    echo "  start-db           Start only databases"
    echo "  stop               Stop all services"
    echo "  restart            Restart all services"
    echo "  status             Show service status"
    echo "  logs [service]     Show logs (optionally for specific service)"
    echo "  health             Check health of all services"
    echo "  cleanup            Remove all containers and volumes (destructive)"
    echo "  reset-db           Reset all databases (destructive)"
    echo "  backup             Backup PostgreSQL and Redis"
    echo "  kafka-topics       List all Kafka topics"
    echo "  kafka-describe     Describe a Kafka topic"
    echo "  psql               Connect to PostgreSQL"
    echo "  redis-cli          Connect to Redis CLI"
    echo "  clickhouse-query   Execute ClickHouse query"
    echo "  help               Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 start"
    echo "  $0 logs kafka"
    echo "  $0 kafka-describe vessel-positions"
    echo "  $0 clickhouse-query 'SELECT count() FROM vessel_monitoring.vessel_positions_history'"
}

# Main script logic
case "$1" in
    start)
        check_docker
        start_all
        ;;
    start-infra)
        check_docker
        start_infra
        ;;
    start-kafka)
        check_docker
        start_kafka
        ;;
    start-db)
        check_docker
        start_databases
        ;;
    stop)
        stop_all
        ;;
    restart)
        check_docker
        restart_all
        ;;
    status)
        show_status
        ;;
    logs)
        show_logs "$2"
        ;;
    health)
        check_health
        ;;
    cleanup)
        cleanup
        ;;
    reset-db)
        reset_databases
        ;;
    backup)
        backup_databases
        ;;
    kafka-topics)
        show_kafka_topics
        ;;
    kafka-describe)
        describe_kafka_topic "$2"
        ;;
    clickhouse-query)
        query_clickhouse "$2"
        ;;
    psql)
        psql_connect
        ;;
    redis-cli)
        redis_cli
        ;;
    help|--help|-h)
        show_help
        ;;
    *)
        print_error "Unknown command: $1"
        echo ""
        show_help
        exit 1
        ;;
esac
