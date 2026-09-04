# E-biessa frontend

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 21.2.21.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Running unit tests

To execute unit tests with the [Vitest](https://vitest.dev/) test runner, use the following command:

```bash
ng test
```

Use the non-interactive test gate before merging a feature:

```bash
npm run test:ci
```

Run the complete frontend verification gate with:

```bash
npm run verify
```

## Architecture and test policy

- Application-wide infrastructure belongs in `src/app/core`.
- Reusable presentational components belong in `src/app/shared`.
- Marketplace, account, messaging, administration, and upcoming AI code should be split into feature folders as they are changed, instead of adding more responsibilities to `App`.
- Every new AI service, component, state transition, or API contract must be introduced with a colocated `*.spec.ts` test.
- AI retrieval behavior must also have backend integration tests covering successful matches, empty results, unavailable services, and invalid responses.

## Running end-to-end tests

The project uses Playwright for browser-level role and AI-search tests with isolated API fixtures:

```bash
npm run test:e2e
```

To exercise the real seeded Spring Boot, FastAPI, and Ollama stack instead of isolated API fixtures, start all services and run:

```bash
npm run test:e2e:live
```

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
