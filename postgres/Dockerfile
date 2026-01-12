FROM postgres:16

USER root

# Change postgres user/group IDs to match the host-mapped IDs
RUN groupmod -g 58400 postgres
RUN usermod  -u 58400 -g 58400 postgres

RUN chown -R postgres:postgres /var/lib/postgresql /var/run/postgresql

USER postgres


# Copy init scripts into the container (00-drop.sql runs first due to naming)
COPY config/00-drop.sql /docker-entrypoint-initdb.d/00-drop.sql
COPY config/init.sql /docker-entrypoint-initdb.d/init.sql

#working now watch out with the persistence
# Expose ports
EXPOSE 5432
