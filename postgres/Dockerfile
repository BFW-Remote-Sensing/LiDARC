FROM postgres:16
ARG CI_BUILD
USER root
RUN if [ -n "$CI_BUILD" ]; then \
    echo "BUILDING FOR PROD: Switching Postgres UID to 58400" && \
    groupmod -g 58400 postgres && \
    usermod -u 58400 -g 58400 postgres && \
    chown -R postgres:postgres /var/lib/postgresql /var/run/postgresql; \
    fi

USER postgres
# Copy init scripts into the container (00-drop.sql runs first due to naming)
COPY config/00-drop.sql /docker-entrypoint-initdb.d/00-drop.sql
COPY config/init.sql /docker-entrypoint-initdb.d/init.sql

#working now watch out with the persistence
# Expose ports
EXPOSE 5432