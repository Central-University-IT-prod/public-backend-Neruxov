FROM python:3.11-slim
WORKDIR /app

COPY app.py .
COPY requirements.txt .
COPY staticmaps staticmaps

RUN python3 -m pip install -r requirements.txt

CMD ["python3", "app.py"]