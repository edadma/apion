// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

// https://astro.build/config
export default defineConfig({
	site: 'https://edadma.github.io',
	base: '/apion',
	integrations: [
		starlight({
			title: 'Apion',
			social: [
				{ icon: 'github', label: 'GitHub', href: 'https://github.com/edadma/apion' },
			],
			sidebar: [
				{
					label: 'Getting Started',
					items: [
						{ label: 'Introduction', slug: 'getting-started/introduction' },
						{ label: 'Installation', slug: 'getting-started/installation' },
						{ label: 'Quick Start', slug: 'getting-started/quick-start' },
					],
				},
				{
					label: 'Core Concepts',
					items: [
						{ label: 'Handlers & Results', slug: 'core/handlers' },
						{ label: 'Request', slug: 'core/request' },
						{ label: 'Response', slug: 'core/response' },
						{ label: 'Routing', slug: 'core/routing' },
						{ label: 'Error Handling', slug: 'core/error-handling' },
					],
				},
				{
					label: 'Middleware',
					items: [
						{ label: 'Overview', slug: 'middleware/overview' },
						{ label: 'Authentication', slug: 'middleware/auth' },
						{ label: 'CORS', slug: 'middleware/cors' },
						{ label: 'Security Headers', slug: 'middleware/security' },
						{ label: 'Logging', slug: 'middleware/logging' },
						{ label: 'Compression', slug: 'middleware/compression' },
						{ label: 'Static Files', slug: 'middleware/static' },
						{ label: 'Cookies', slug: 'middleware/cookies' },
						{ label: 'Rate Limiting', slug: 'middleware/rate-limiting' },
						{ label: 'File Uploads', slug: 'middleware/file-uploads' },
						{ label: 'Body Limit', slug: 'middleware/body-limit' },
					],
				},
				{
					label: 'Guides',
					items: [
						{ label: 'Building an API Server', slug: 'guides/api-server' },
						{ label: 'Custom Middleware', slug: 'guides/custom-middleware' },
						{ label: 'JWT & Custom Tokens', slug: 'guides/jwt' },
						{ label: 'Testing', slug: 'guides/testing' },
					],
				},
				{
					label: 'Reference',
					items: [
						{ label: 'Response DSL', slug: 'reference/response-dsl' },
						{ label: 'Changelog', slug: 'reference/changelog' },
					],
				},
			],
		}),
	],
});
