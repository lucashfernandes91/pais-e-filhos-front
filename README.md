# Frontend - Pais e Filhos (CoParent Lite)

Aplicativo Android do projeto **Pais e Filhos**, voltado para organizacao da coparentalidade com comunicacao em tempo real, agenda compartilhada e registro estruturado de interacoes entre responsaveis.

Este modulo concentra a experiencia mobile do produto, incluindo onboarding, autenticacao, chat, agenda, linha do tempo, perfil, notificacoes e exportacao de registros via integracao com o backend.

## Visao geral

O app foi construido em **Kotlin** para Android e consome a API do backend Django por meio de **Retrofit/OkHttp**. Para mensagens em tempo real, usa **WebSocket**. Para notificacoes push, integra com **Firebase Cloud Messaging (FCM)**.

Embora o projeto esteja habilitado para **Jetpack Compose**, a interface atual e majoritariamente baseada em **Activities, Fragments e layouts XML**.

## Funcionalidades principais

- Onboarding e fluxo de entrada no app.
- Cadastro de usuario, login e recuperacao de senha por codigo.
- Chat entre responsaveis com mensagens em tempo real.
- Envio de anexos em conversas.
- Agenda compartilhada com criacao, edicao e exclusao de eventos.
- Linha do tempo unificada de mensagens e eventos.
- Cadastro e edicao de filhos, incluindo foto e dados complementares.
- Notificacoes in-app e push.
- Exportacao de mensagens e eventos em PDF.
- Atalhos por deep link para telas principais.
- Integracao com Google Calendar para adicionar eventos ao calendario do dispositivo.

## Stack

- Kotlin
- Android SDK
- AndroidX
- Material Components
- Navigation Component
- Retrofit
- OkHttp
- Gson Converter
- Firebase Cloud Messaging
- Glide
- SharedPreferences

## Requisitos

- Android Studio atualizado
- JDK 11
- Android SDK configurado localmente
- `minSdk 24`
- `targetSdk 36`
- Backend do projeto em execucao

## Configuracao local

O app monta a URL da API e do WebSocket a partir do arquivo `local.properties`.

Exemplo:

```properties
sdk.dir=C:\\Users\\SEU_USUARIO\\AppData\\Local\\Android\\Sdk
dev.host=10.0.2.2
dev.port=8000
```

### Parametros importantes

- `dev.host=10.0.2.2`: usar quando o app roda no emulador Android e o backend esta na maquina local.
- `dev.host=localhost`: usar em cenarios com redirecionamento local apropriado.
- `dev.host=192.168.x.x`: usar quando o app roda em dispositivo fisico na mesma rede do backend.
- `dev.port=8000`: porta padrao do backend neste projeto.

Se `dev.host` e `dev.port` nao forem definidos, o app usa por padrao:

- API: `http://10.0.2.2:8000/`
- WebSocket: `ws://10.0.2.2:8000/`

## Seguranca de rede

- Em **debug**, o projeto permite trafego HTTP apenas para hosts locais de desenvolvimento, como `10.0.2.2`, `localhost` e `127.0.0.1`.
- Em **release**, o app bloqueia cleartext e espera comunicacao segura via HTTPS.

## Firebase

O projeto utiliza o plugin `com.google.gms.google-services` e depende do arquivo `app/google-services.json` para recursos de notificacao push.

Se voce conectar o app a outro projeto Firebase, substitua esse arquivo pelo correspondente ao novo ambiente.

## Como executar

### 1. Suba o backend

Garanta que a API do projeto esteja rodando localmente e acessivel pela porta configurada no `local.properties`.

### 2. Abra o modulo Android

No Android Studio, abra a pasta:

```text
frontend/
```

### 3. Sincronize o Gradle

O projeto usa Gradle Kotlin DSL e catalogo de versoes em `gradle/libs.versions.toml`.

### 4. Rode o app

Via Android Studio:

- selecione um emulador ou dispositivo
- execute a configuracao `app`

Via terminal no Windows:

```powershell
cd frontend
.\gradlew.bat assembleDebug
```

Via terminal no Linux/macOS:

```bash
cd frontend
./gradlew assembleDebug
```

## Estrutura do projeto

```text
frontend/
|-- app/
|   |-- src/main/java/com/example/chatapp/
|   |   |-- ApiService.kt
|   |   |-- RetrofitClient.kt
|   |   |-- WebSocketManager.kt
|   |   |-- ChatMessagingService.kt
|   |   |-- PrefsHelper.kt
|   |   |-- MainActivity.kt
|   |   |-- LoginActivity.kt
|   |   |-- RegisterActivity.kt
|   |   |-- ForgotPasswordActivity.kt
|   |   |-- VerifyResetCodeActivity.kt
|   |   |-- NewPasswordActivity.kt
|   |   `-- ui/
|   |       |-- HomeFragment.kt
|   |       |-- ChatFragment.kt
|   |       |-- AgendaFragment.kt
|   |       |-- TimelineFragment.kt
|   |       |-- ProfileFragment.kt
|   |       |-- NotificationsFragment.kt
|   |       |-- NotificationSettingsFragment.kt
|   |       |-- ChildDetailFragment.kt
|   |       |-- EditChildFragment.kt
|   |       `-- ExportBottomSheet.kt
|   |-- src/main/res/
|   |   |-- layout/
|   |   |-- drawable/
|   |   |-- navigation/
|   |   |-- values/
|   |   `-- xml/
|   `-- google-services.json
|-- gradle/
|-- build.gradle.kts
|-- settings.gradle.kts
`-- gradlew.bat
```

## Organizacao tecnica

### Navegacao e telas

- `OnboardingActivity` e o ponto de entrada do app.
- `LoginActivity`, `RegisterActivity` e telas de recuperacao de senha cobrem autenticacao.
- `MainActivity` hospeda o `NavHostFragment` e a navegacao principal por bottom navigation.
- As features principais ficam em `com.example.chatapp.ui`.

### Camada de rede

- `ApiService.kt` concentra os endpoints REST.
- `RetrofitClient.kt` configura o cliente HTTP e a URL base.
- `AuthInterceptor.kt` adiciona credenciais e cabecalhos as requisicoes.

### Tempo real

- `WebSocketManager.kt` gerencia conexao, reconexao e recebimento de mensagens em tempo real.

### Notificacoes

- `ChatMessagingService.kt` recebe eventos do Firebase e registra o token do dispositivo no backend.
- O app suporta deep links como `coparent://chat`, `coparent://home` e `coparent://notifications`.

### Estado local

- `PrefsHelper.kt` centraliza persistencia em `SharedPreferences`, incluindo sessao, configuracoes e dados auxiliares do usuario.

## Fluxos cobertos no app

- Onboarding e criacao de conta
- Login e manutencao de sessao
- Recuperacao de senha com codigo
- Conversa entre responsaveis
- Leitura, busca e status de mensagens
- Agenda de eventos
- Linha do tempo consolidada
- Perfil e cadastro dos filhos
- Configuracoes de notificacao
- Exportacao de PDF e compartilhamento

## Observacoes para desenvolvimento

- O nome tecnico do modulo ainda aparece em alguns pontos como `ChatApp`, mas o branding funcional do produto no app e `CoParent Lite`.
- O projeto inclui recursos de acessibilidade, estados de erro/carregamento e suporte a temas.
- Alguns componentes de UI e estilos foram organizados para reutilizacao em `res/values` e `component_styles.xml`.

## Integracao com o backend

O frontend depende diretamente dos seguintes grupos de recursos do backend:

- autenticacao e perfil
- conversas e mensagens
- anexos
- eventos e agenda
- notificacoes
- cadastro de filhos
- exportacao de PDF

Se endpoints, payloads ou contratos de autenticacao mudarem no backend, este modulo precisara ser atualizado em `ApiService.kt`, DTOs e telas consumidoras.
