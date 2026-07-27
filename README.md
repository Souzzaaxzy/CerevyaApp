# CerevyaApp

**Cerevya** é um aplicativo Android que funciona como seu "segundo cérebro digital" - um assistente pessoal para salvar memórias, ideias e pensamentos.

## ✨ Funcionalidades

- 💬 **Chat com IA**: Conversas inteligentes usando Groq API
- 🧠 **Sistema de Memórias**: Salve, pesquise e organize suas ideias
- 🔐 **Autenticação Google**: Login seguro via Firebase
- 📱 **Offline-First**: Funciona offline e sincroniza quando conectado
- 🎨 **Temas**: Suporte a tema claro, escuro e automático

## 🛠️ Configuração

### 1. Variáveis de Ambiente

Crie um arquivo `secrets.properties` na raiz do projeto (baseado em `secrets.properties.example`):

```properties
GROQ_API_KEY=sua_chave_groq_aqui
GOOGLE_WEB_CLIENT_ID=seu_client_id_google_aqui
```

### 2. Dependências

```bash
./gradlew assembleDebug
```

### 3. Executar

```bash
./gradlew installDebug
```

## 📁 Estrutura do Projeto

```
CerevyaApp/
├── app/
│   └── src/main/java/com/cerevya/
│       ├── ai/              # Integração com IA (Groq API)
│       ├── auth/            # Autenticação Firebase
│       ├── data/            # Camada de dados (Room, Firestore)
│       │   ├── chat/        # Repositório de chat
│       │   ├── database/    # Room Database
│       │   ├── firestore/   # Firestore Manager
│       │   └── preferences/ # Preferências do app
│       ├── domain/          # Lógica de domínio
│       │   └── models/      # Entidades do domínio
│       ├── navigation/      # Navegação Compose
│       ├── sync/            # Gerenciamento de sync
│       ├── theme/           # Temas Material3
│       ├── ui/              # Interface Compose
│       │   ├── components/  # Componentes reutilizáveis
│       │   └── screens/     # Telas do app
│       └── viewmodel/       # ViewModels
└── build.gradle.kts         # Configuração de build
```

## 🧪 Tecnologias

- **Kotlin** - Linguagem principal
- **Jetpack Compose** - UI moderna
- **Room** - Banco de dados local
- **Firebase Auth** - Autenticação
- **Firebase Firestore** - Backend em nuvem
- **Groq API** - Processamento de linguagem natural
- **Coroutines + Flow** - Programação assíncrona
- **Hilt/Koin** - Injeção de dependências (futuro)

## 📝 Licença

MIT License
