#!/bin/bash
set -e

cd "$(dirname "$0")"

usage() {
    echo "Usage: $0 {up|down|restart|logs|status|build}"
    echo ""
    echo "  up       Build and start all services (detached)"
    echo "  down     Stop and remove all services"
    echo "  restart  Restart all services"
    echo "  logs     Tail logs (Ctrl+C to stop)"
    echo "  status   Show service status"
    echo "  build    Build images without starting"
    exit 1
}

case "${1:-up}" in
    up)
        echo "Building and starting services..."
        docker compose up --build -d
        echo ""
        echo "Services started:"
        echo "  Frontend:  http://localhost"
        echo "  Backend:   http://localhost:8080"
        echo "  Agent:     localhost:9090 (gRPC)"
        echo ""
        echo "Run '$0 logs' to view output"
        ;;
    down)
        echo "Stopping services..."
        docker compose down
        ;;
    restart)
        echo "Restarting services..."
        docker compose down
        docker compose up --build -d
        echo "Services restarted"
        ;;
    logs)
        docker compose logs -f
        ;;
    status)
        docker compose ps
        ;;
    build)
        echo "Building images..."
        docker compose build
        echo "Build complete. Run '$0 up' to start"
        ;;
    *)
        usage
        ;;
esac
