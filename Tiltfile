# Dev loop for hermes-audiobooks-api on the olympus kind cluster.  Run:  tilt up   (UI: http://localhost:10350)
allow_k8s_contexts('kind-olympus')

# Builds the image, pushes it to the local registry and swaps it into the Deployment on every change.
docker_build('localhost:5001/hermes-audiobooks-api', '.')

k8s_yaml(kustomize('k8s/overlays/local'))
k8s_resource('hermes-audiobooks-api', port_forwards='8080:8080', labels=['apps'],
             links=[link('http://hermes.localtest.me', 'ingress')])
