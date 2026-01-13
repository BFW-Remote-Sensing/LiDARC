FROM postgres:16

RUN if [ -n "$CI" ]; then \
    USER root && \
    groupmod -g 58400 postgres && \
    usermod -u 58400 -g 58400 postgres && \
    chown -R postgres:postgres /var/lib/postgresql /var/run/postgresql && \
    USER postgres; \
    fi

# Copy init scripts into the container (00-drop.sql runs first due to naming)
COPY config/00-drop.sql /docker-entrypoint-initdb.d/00-drop.sql
COPY config/init.sql /docker-entrypoint-initdb.d/init.sql

#working now watch out with the persistence
# Expose ports
EXPOSE 5432