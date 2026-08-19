FROM postgres:17-alpine
COPY --chmod=755 deploy/postgres/init-services.sh /docker-entrypoint-initdb.d/01-services.sh
RUN chmod 755 /docker-entrypoint-initdb.d/01-services.sh
