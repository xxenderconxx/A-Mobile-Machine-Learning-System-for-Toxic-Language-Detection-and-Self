# syntax=docker/dockerfile:1.7
FROM ghcr.io/cirruslabs/android-sdk:34

ARG SOURCE_REVISION=unknown
LABEL org.opencontainers.image.title="Toxic Language Detection thesis repository workspace"
LABEL org.opencontainers.image.source="https://github.com/xxenderconxx/A-Mobile-Machine-Learning-System-for-Toxic-Language-Detection-and-Self"
LABEL org.opencontainers.image.revision="$SOURCE_REVISION"

WORKDIR /workspace

RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 python3-venv python3-pip \
    && rm -rf /var/lib/apt/lists/*

COPY requirements-docker.txt /tmp/requirements-docker.txt
RUN python3 -m venv /opt/venv \
    && /opt/venv/bin/pip install --no-cache-dir -r /tmp/requirements-docker.txt
ENV PATH="/opt/venv/bin:$PATH"
ENV NLTK_DATA="/usr/local/share/nltk_data"

RUN python -m nltk.downloader -d /usr/local/share/nltk_data wordnet omw-1.4

COPY . .

# Build the APK with a temporary placeholder Firebase configuration.
RUN set -eu; \
    printf '%s\n' '{"project_info":{"project_number":"000000000000","project_id":"docker-build-placeholder","storage_bucket":"docker-build-placeholder.appspot.com"},"client":[{"client_info":{"mobilesdk_app_id":"1:000000000000:android:0000000000000000000000","android_client_info":{"package_name":"com.example.toxiclanguagekeyboard"}},"api_key":[{"current_key":"AIzaSyAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"}]}],"configuration_version":"1"}' \
      > ToxicLanguageKeyboard/app/google-services.json; \
    trap 'rm -f ToxicLanguageKeyboard/app/google-services.json' EXIT; \
    sed -i 's/\r$//' ToxicLanguageKeyboard/gradlew; \
    chmod +x ToxicLanguageKeyboard/gradlew; \
    cd ToxicLanguageKeyboard; \
    ./gradlew --no-daemon assembleDebug

# Keep the container running so Docker Desktop can browse /workspace.
CMD ["sleep", "infinity"]
