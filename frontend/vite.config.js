import path from 'path';

export default {
    css: {
        tansformer: 'lightningcss',
        preprocessorOptions: {
            scss: {
                silenceDeprecations: ['color-functions', 'global-builtin', 'import', 'legacy-js-api'],
            },
        }
    },
    root: ".",
    resolve: {
        alias: {
            '~bootstrap': path.resolve(__dirname, 'node_modules/bootstrap'),
        }
    },
    server: {
        port: 5173,
        hot: true,
        proxy: {
            '/verify': {
                target: "http://localhost:8085",
                changeOrigin: true,
                secure: false,
            },
            '/challenge': {
                target: "http://localhost:8085",
                changeOrigin: true,
                secure: false,
            },
            '/api': {
                target: "http://localhost:8085",
                changeOrigin: true,
                secure: false,
            },
            '/whoami': {
                target: "http://localhost:8085",
                changeOrigin: true,
                secure: false,
            },
            '/login': {
                target: "http://localhost:8085",
                changeOrigin: true,
                secure: false,
            },
            '/ott': {
                target: "http://localhost:8085",
                changeOrigin: true,
                secure: false,
            },
            '/webauthn': {
                target: "http://localhost:8085",
                changeOrigin: true,
                secure: false,
            },
            '/uploadKalender': {
                target: "http://localhost:8085",
                changeOrigin: true,
                secure: false,
            },
            '/randomFeedback': {
                target: "http://localhost:8085",
                changeOrigin: true,
                secure: false,
            },
            '/feedback': {
                target: "http://localhost:8085",
                changeOrigin: true,
                secure: false,
            },
            '/removeMyOffers': {
                target: "http://localhost:8085",
                changeOrigin: true,
                secure: false,
            },
            '/createOffer': {
                target: "http://localhost:8085",
                changeOrigin: true,
                secure: false,
            },
            '/removeTermin': {
                target: "http://localhost:8085",
                changeOrigin: true,
                secure: false,
            },
            '/betaLogin': {
                target: "http://localhost:8085",
                changeOrigin: true,
                secure: false,
            },
            '/myKalender': {
                target: "http://localhost:8085",
                changeOrigin: true,
                secure: false,
            },
            '/acceptOffer': {
                target: "http://localhost:8085",
                changeOrigin: true,
                secure: false,
            },
            '/updatePrivateMail': {
                target: "http://localhost:8085",
                changeOrigin: true,
                secure: false,
            },
            '/logmeout': {
                target: "http://localhost:8085",
                changeOrigin: true,
                secure: false,
            },
        },
        headers: {
            "Referrer-Policy": "same-origin",
        }
    },


    build: {
        cssMinify: "lightningcss",
        rollupOptions: {
            input: {
                index: path.resolve(__dirname, 'index.html'),
                admin: path.resolve(__dirname, 'admin.html'),
                "Impressum und Datenschutz": path.resolve(__dirname, 'Impressum und Datenschutz.html'),
                "evaluation": path.resolve(__dirname, 'evaluation.html'),
            }
        },
        output: { dir: 'dist', }
    },

}
