# QwikRide - Bike Sharing System

A comprehensive bike sharing platform.

## Team Members

- **Abdul Rehman** - 40279024  
- **Salima Ammar** - 40283429  
- **Aymen Mefti** - 40299611  
- **Karim Mawji** - 40281154  
- **Yanis Djeridi** - 40227313  

## Tech Stack

### Frontend Technologies
- **React 19** - A JavaScript library for building user interfaces, particularly single-page applications
- **Vite** - A modern build tool that provides fast development server and optimized production builds
- **Tailwind CSS** - A utility-first CSS framework for rapidly building custom user interfaces
- **Framer Motion** - A production-ready motion library for React that provides smooth animations
- **React Router** - A standard library for routing in React applications, enabling navigation between pages
- **Axios** - A promise-based HTTP client for making API requests to the backend

### Backend Technologies

- **Spring Boot 3.4** - A Java framework that simplifies the development of production-ready applications
- **Spring Security** - A powerful authentication and access-control framework for Java applications
- **Spring Data JPA** - A framework that simplifies database operations using Java Persistence API
- **Supabase (PostgreSQL)** - A cloud-based PostgreSQL database with built-in authentication and real-time features
- **JWT (JSON Web Tokens)** - A secure way to transmit information between parties as a JSON object
- **Maven** - A build automation tool used primarily for Java projects to manage dependencies and build processes

## Project Structure

```
SOEN-343---Bike/
├── qwikride-frontend/          # React frontend application
│   ├── src/
│   │   ├── components/         # Reusable UI components
│   │   │   └── Navbar.jsx
│   │   ├── pages/             # Page components
│   │   │   ├── Home.jsx
│   │   │   ├── Login.jsx
│   │   │   ├── Register.jsx
│   │   │   └── Dashboard.jsx
│   │   ├── context/           # React context providers
│   │   │   ├── AuthContext.jsx
│   │   │   └── ThemeContext.jsx
│   │   ├── hooks/             # Custom React hooks
│   │   │   ├── useAuth.js
│   │   │   └── useTheme.js
│   │   ├── services/          # API service layer
│   │   │   └── api.js
│   │   └── App.jsx            # Main application component
│   ├── package.json
│   └── tailwind.config.js
├── qwikride-backend/           # Spring Boot backend application
│   ├── src/main/java/com/qwikride/
│   │   ├── config/            # Configuration classes
│   │   ├── controller/        # REST controllers
│   │   ├── dto/              # Data Transfer Objects
│   │   ├── model/            # Entity models
│   │   ├── repository/        # Data repositories
│   │   ├── service/          # Business logic
│   │   └── util/             # Utility classes
│   └── pom.xml
└── README.md
```

## Getting Started

### Prerequisites

- **Node.js** (v18 or higher) - JavaScript runtime for frontend development
- **Java 17** - Programming language for backend development
- **Maven 3.6+** - Build automation tool for Java projects

### Backend Setup

The backend is built using **Spring Boot**, a Java framework that simplifies the development of web applications. Here's how to set it up:

#### What is Maven?

**Maven** is a build automation tool used primarily for Java projects. It manages project dependencies, compiles code, runs tests, and packages applications. Maven uses a `pom.xml` (Project Object Model) file to define project configuration.

#### Understanding pom.xml

The `pom.xml` file is Maven's configuration file that contains:
- Project metadata (name, version, description)
- Dependencies (libraries your project needs)
- Build configuration (how to compile and package)
- Plugin configurations

#### Setup Steps:

1. Navigate to the backend directory:
   ```bash
   cd qwikride-backend
   ```

2. Configure database credentials:
   - Copy `.env.example` to `.env`:
     ```bash
     cp .env.example .env
     ```
   - Edit `.env` and add your Supabase database password:
     ```
     DB_PASSWORD=your_database_password_here
     ```
   - **Note**: The database password is different from the API key. Find it in your Supabase Dashboard under Project Settings > Database.

3. Run the Spring Boot application:
   ```bash
   ./mvnw spring-boot:run
   ```
   
   Or on Windows:
   ```bash
   ./mvnw.cmd spring-boot:run
   ```

   **What happens here?**
   - `mvnw` (Maven Wrapper) downloads Maven if not present
   - Maven reads `pom.xml` to understand project dependencies
   - Spring Boot connects to Supabase PostgreSQL database
   - Spring Boot starts an embedded web server
   - The application becomes available at `http://localhost:8080`

4. The backend will start on `http://localhost:8080`

### Frontend Setup

The frontend is built using **React**, a JavaScript library for building user interfaces, with **Vite** as the build tool.

#### What is Vite?

**Vite** is a modern build tool that provides:
- Fast development server with hot module replacement
- Optimized production builds
- Native ES modules support

#### Understanding package.json

The `package.json` file is Node.js's equivalent of `pom.xml` and contains:
- Project metadata and scripts
- Dependencies (libraries your project needs)
- Development dependencies
- Build and run scripts

#### Setup Steps:

1. Navigate to the frontend directory:
   ```bash
   cd qwikride-frontend
   ```

2. Install dependencies:
   ```bash
   npm install
   ```
   
   **What happens here?**
   - npm reads `package.json` to identify required packages
   - Downloads and installs all dependencies to `node_modules/`
   - Creates a lock file (`package-lock.json`) for consistent installs

3. Start the development server:
   ```bash
   npm run dev
   ```
   
   **What happens here?**
   - Vite starts a development server
   - Enables hot module replacement (changes reflect instantly)
   - Serves the application at `http://localhost:5173`

4. The frontend will start on `http://localhost:5173`

## Authentication

The system supports two user roles:
- **Rider** - Regular users who can find and use bikes
- **Operator** - System administrators who manage stations and fleet

### Test Accounts

- **Operator**: `operator` / `operator123`

## Features

### Frontend Features

- **Modern UI** - Clean, responsive design with dark/light theme
- **Authentication** - Secure login and registration
- **Responsive** - Mobile-first design
- **Animations** - Smooth transitions with Framer Motion
- **Role-based Dashboards** - Different views for riders and operators

### Backend Features

- **JWT Authentication** - Secure token-based authentication
- **Spring Security** - Comprehensive security configuration
- **RESTful API** - Clean API endpoints
- **Data Persistence** - JPA with Supabase PostgreSQL database
- **Input Validation** - Request validation and error handling

## Available Scripts

### Frontend Scripts

- `npm run dev` - Starts the Vite development server with hot module replacement
- `npm run build` - Creates an optimized production build of the application
- `npm run preview` - Serves the production build locally for testing
- `npm run lint` - Runs ESLint to check code quality and find potential issues

### Backend Scripts

- `./mvnw spring-boot:run` - Compiles and runs the Spring Boot application
- `./mvnw test` - Executes all unit tests in the project
- `./mvnw clean package` - Cleans previous builds and creates a JAR file for deployment

## Diagrams

UML diagrams for the system are available in the `diagrams/` folder:

- **Use Case Diagram** - Shows all actors and their use cases
- **Sequence Diagrams** - Shows registration, login, and authentication flows

See [diagrams/README.md](diagrams/README.md) for viewing instructions.

## API Endpoints

### Authentication

- `POST /api/auth/register` - User registration
- `POST /api/auth/login` - User login





