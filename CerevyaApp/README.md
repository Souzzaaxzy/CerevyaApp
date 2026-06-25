# Cerevya - Seu Segundo Cérebro Digital

<p align="center">
  <img src="https://img.shields.io/badge/Version-1.0.0-blue.svg" alt="Version">
  <img src="https://img.shields.io/badge/Platform-Android-green.svg" alt="Platform">
  <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License">
</p>

## 📱 Descrição

Cerevya é um aplicativo Android de "segundo cérebro digital" que permite salvar e recuperar memórias pessoais através de uma interface de chat simples e elegante.

## ✨ Funcionalidades

### Fase 1 (MVP)
- [x] **Splash Screen** - Tela de abertura com logo e animação
- [x] **Chat Screen** - Interface de chat para conversar e salvar memórias
- [x] **Memory Screen** - Lista de memórias salvas com pesquisa
- [x] **Settings Screen** - Informações do aplicativo
- [x] **Navigation Drawer** - Menu lateral para navegação
- [x] **Sistema de Memória** - Salvar memórias com comandos de voz

### Comandos do Chat
- `salva isso: [texto]` - Salva uma nova memória
- `lembra disso: [texto]` - Salva uma nova memória (alternativo)
- `guarda isso: [texto]` - Salva uma nova memória (alternativo)
- `mostrar memórias` - Lista todas as memórias salvas

## 🛠️ Tecnologias

- **Kotlin** - Linguagem principal
- **Jetpack Compose** - UI moderna
- **Navigation Compose** - Navegação
- **Room Database** - Armazenamento local
- **Material Design 3** - Design system
- **MVVM** - Arquitetura
- **Repository Pattern** - Camada de dados

## 📁 Estrutura do Projeto

```
app/src/main/java/com/cerevya/
├── data/
│   ├── database/       # Room Database
│   └── repository/      # Repositories
├── domain/
│   └── models/         # Entidades
├── navigation/         # Navegação
├── theme/              # Tema Material 3
├── ui/
│   ├── components/     # Componentes reutilizáveis
│   └── screens/        # Telas do app
└── viewmodel/          # ViewModels
```

## 🚀 Como Executar

1. Clone o repositório:
```bash
git clone https://github.com/Souzzaaxzy/CerevyaApp.git
```

2. Abra o projeto no Android Studio

3. Execute no emulador ou dispositivo

## 📋 Requisitos

- Android SDK 34+
- Kotlin 1.9.22+
- Java 21

## 📝 Roadmap

### Fase 2 (Planejado)
- [ ] Integração com IA para respostas inteligentes
- [ ] Backup em nuvem
- [ ] Login com Google
- [ ] Notificações

### Fase 3 (Futuro)
- [ ] Upload de arquivos
- [ ] Sincronização multi-dispositivo
- [ ] Tags e categorias avançadas
- [ ] Exportar/Importar dados

## 📄 Licença

Este projeto está sob a licença MIT. Veja o arquivo [LICENSE](LICENSE) para mais detalhes.

---

Desenvolvido com ❤️ para o seu segundo cérebro digital.
