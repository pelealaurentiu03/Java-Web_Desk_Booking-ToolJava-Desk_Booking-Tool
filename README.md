# Desk Booking Web Application

A full-stack web application designed to streamline office space management and desk reservations. Built using Java and the Vaadin framework, this tool provides a seamless, single-page application (SPA) experience for employees to book desks and for administrators to manage office resources.

## Project Architecture

The application is built with a focus on clean code and modularity, leveraging Java's object-oriented strengths:

* **User Interface (/ui & /frontend):** Built with Vaadin components, ensuring a responsive and intuitive user experience without the need for separate REST controllers for the frontend.
* **Authentication & Security (/auth):** A dedicated layer managing secure user login, session handling, and role-based access control.
* **Database Management (/database):** Handles data persistence for users, office maps, and booking transactions.
* **Business Logic:** Centralized service layer that coordinates between the UI and the data persistence layers.

## Technical Stack

* **Backend & UI Framework:** Java with Vaadin
* **Build & Dependency Management:** Maven (`pom.xml`)
* **Frontend Assets:** TypeScript and CSS (managed via Vaadin's internal Vite integration)
* **Environment Configuration:** Managed via `.env` for secure local development.

## Repository Structure

* `/src/main/java/.../ui` - Contains the Vaadin views and UI component logic.
* `/src/main/java/.../auth` - Security configurations and authentication providers.
* `/src/main/java/.../database` - Entity models and data access objects (DAOs).
* `/src/main/frontend/themes` - Custom CSS styling for the application's look and feel.
* `pom.xml` - Project object model defining all Java dependencies.

## Running Locally

**Note for Reviewers:** Sensitive configuration files (such as `.env`) have been excluded via `.gitignore`. 

To run this project:
1. Ensure you have **Java 17+** and **Maven** installed.
2. Clone the repository.
3. Configure your local database settings in a `.env` file or `application.properties`.
4. Launch the application using the Maven wrapper: 
   ```bash
   ./mvnw spring-boot:run
5. Access the tool at http://localhost:8080.
