FROM fedora:41
WORKDIR /app
COPY target/oda-subscriptions-service /app

CMD ["./oda-subscriptions-service"]
