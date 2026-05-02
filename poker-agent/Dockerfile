FROM python:3.11-slim
WORKDIR /app
COPY pyproject.toml ./
COPY config/ config/
COPY proto/ proto/
COPY scripts/ scripts/
COPY src/ src/
RUN pip install --no-cache-dir -i https://mirrors.aliyun.com/pypi/simple/ .
EXPOSE 9090
CMD ["python", "-m", "poker_agent.grpc.server", "--config", "config/default.yaml", "--bind", "0.0.0.0:9090"]
