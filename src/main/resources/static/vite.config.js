import path from 'path';

export default {
    css: {
        tansformer: 'lightningcss'
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

            }
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
                "Impressum und Datenschutz": path.resolve(__dirname, 'Impressum und Datenschutz.html'),
            }
        },
        output: { dir: 'dist', }
    },

}
