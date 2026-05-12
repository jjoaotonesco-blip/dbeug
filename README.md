# Ask Bar Android

MVP em Kotlin para uma barra flutuante que chama a Groq API com o modelo `qwen/qwen3-32b`.

## Como usar

1. Abre esta pasta no Android Studio: `AskBarAndroid`.
2. Faz sync do Gradle.
3. Instala no telemovel.
4. Abre a app e cola a tua Groq API key no campo `Groq API key`.
5. Toca em `Ativar permissao de pop-up`.
6. Toca em `Abrir barra agora`.
7. Para o atalho na barra do Android, edita os Quick Settings e adiciona o tile `Ask Bar`.

## Testar sem Android Studio

O projeto inclui um workflow do GitHub Actions que gera o APK na cloud.

1. Cria um repositorio no GitHub.
2. Faz upload/push da pasta `AskBarAndroid`.
3. Vai a `Actions`.
4. Abre `Android Debug APK`.
5. Clica `Run workflow`.
6. Quando terminar, baixa o artifact `ask-bar-debug-apk`.
7. Instala o `app-debug.apk` no telemovel.

## Onde fica a API key?

A key fica guardada localmente no telemovel em `SharedPreferences`. Para um produto publico, troca isto por armazenamento encriptado ou por um backend teu.

## Modelo

O modelo por defeito e:

```text
qwen/qwen3-32b
```

Podes mudar no campo `Modelo` dentro da app.
