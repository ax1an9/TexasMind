FROM python:3.11-slim
RUN pip install --no-cache-dir uv
WORKDIR /app
COPY pyproject.toml ./
COPY config/ config/
COPY proto/ proto/
COPY scripts/ scripts/
COPY src/ src/
RUN uv sync --no-dev
EXPOSE 9090
CMD ["uv", "run", "python", "-m", "poker_agent.grpc.server", "--config", "config/default.yaml", "--bind", "0.0.0.0:9090"]
