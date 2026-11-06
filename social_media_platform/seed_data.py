import os
import django

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'social_network.settings')
django.setup()

from django.contrib.auth.models import User
from core.models import (
    UserProfile, Connection, Follow, Group, CompanyPage,
    Post, Comment, Message, Notification
)

def run_seed():
    print("Starting Social Media Platform database seed...")

    # 1. Admin Superuser
    admin_user, created = User.objects.get_or_create(username='admin', defaults={
        'email': 'admin@smartsearch.com',
        'first_name': 'Admin',
        'last_name': 'Director',
        'is_staff': True,
        'is_superuser': True,
    })
    admin_user.set_password('Admin@123')
    admin_user.save()

    admin_profile = admin_user.profile
    admin_profile.headline = "Chief Technology Officer & Lead Architect @ SmartSearch"
    admin_profile.profession = "Chief Technology Officer"
    admin_profile.company_name = "SmartSearch Innovations"
    admin_profile.location = "San Francisco, CA"
    admin_profile.education = "M.S. in Computer Science, Stanford University"
    admin_profile.bio = "Passionate about scalable distributed search infrastructure, high-throughput indexing and modern social networking architectures. Leading the SmartSearch ecosystem."
    admin_profile.skills = "Python, Django, Spring Boot, Lucene, Distributed Systems, Cloud Architecture"
    admin_profile.interests = "Search Engines, Real-time Protocols, AI Agents"
    admin_profile.avatar_url = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=256&q=80"
    admin_profile.cover_photo = None
    admin_profile.save()

    # 2. Regular Tech Users
    demo_users_data = [
        {
            'username': 'alex_dev',
            'email': 'alex@cloudscale.io',
            'first_name': 'Alex',
            'last_name': 'Rivera',
            'password': 'Password@123',
            'headline': 'Senior Backend Engineer @ CloudScale Labs | Open Source Contributor',
            'profession': 'Senior Backend Engineer',
            'company': 'CloudScale Labs',
            'location': 'Bengaluru, India',
            'education': 'B.Tech in Computer Engineering, IIT Delhi',
            'bio': 'Building resilient microservices and distributed database systems. Love Python, Django, Docker, and Kafka.',
            'skills': 'Python, Django, Kubernetes, Docker, PostgreSQL, Redis, Microservices',
            'avatar': 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=256&q=80',
        },
        {
            'username': 'sarah_ai',
            'email': 'sarah.chen@apexai.org',
            'first_name': 'Dr. Sarah',
            'last_name': 'Chen',
            'password': 'Password@123',
            'headline': 'AI & NLP Research Lead @ Apex AI Labs | Keynote Speaker',
            'profession': 'Principal AI Researcher',
            'company': 'Apex AI Labs',
            'location': 'London, United Kingdom',
            'education': 'Ph.D. in Artificial Intelligence, University of Oxford',
            'bio': 'Exploring large multimodal foundation models, retrieval-augmented generation and ethical AI frameworks.',
            'skills': 'Machine Learning, PyTorch, LLMs, NLP, Generative AI, Python',
            'avatar': 'https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?auto=format&fit=crop&w=256&q=80',
        },
        {
            'username': 'michael_product',
            'email': 'michael.vance@smartsearch.com',
            'first_name': 'Michael',
            'last_name': 'Vance',
            'password': 'Password@123',
            'headline': 'VP of Product Strategy @ SmartSearch | Ex-Google',
            'profession': 'VP of Product',
            'company': 'SmartSearch Innovations',
            'location': 'New York, USA',
            'education': 'MBA, Harvard Business School',
            'bio': 'Obsessed with user experience, search relevance and building collaborative social products.',
            'skills': 'Product Management, Agile, User Research, Data Analytics, Growth Strategy',
            'avatar': 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=256&q=80',
        },
        {
            'username': 'emily_frontend',
            'email': 'emily.weber@creativecore.de',
            'first_name': 'Emily',
            'last_name': 'Weber',
            'password': 'Password@123',
            'headline': 'Design Systems & Frontend Architect @ CreativeCore',
            'profession': 'Frontend Architect',
            'company': 'CreativeCore Systems',
            'location': 'Berlin, Germany',
            'education': 'B.S. in Interactive Media, TU Berlin',
            'bio': 'Crafting pixel-perfect, accessible, and ultra-fast user experiences with modern HTML5, CSS and JavaScript.',
            'skills': 'UI/UX Design, CSS3, JavaScript, TypeScript, Bootstrap, Accessibility',
            'avatar': 'https://images.unsplash.com/photo-1580489944761-15a19d654956?auto=format&fit=crop&w=256&q=80',
        }
    ]

    users = {'admin': admin_user}
    for udata in demo_users_data:
        u, c = User.objects.get_or_create(username=udata['username'], defaults={
            'email': udata['email'],
            'first_name': udata['first_name'],
            'last_name': udata['last_name'],
        })
        u.set_password(udata['password'])
        u.save()

        p = u.profile
        p.headline = udata['headline']
        p.profession = udata['profession']
        p.company_name = udata['company']
        p.location = udata['location']
        p.education = udata['education']
        p.bio = udata['bio']
        p.skills = udata['skills']
        p.avatar_url = udata['avatar']
        p.save()
        users[udata['username']] = u

    # 3. Create Communities / Groups
    groups_data = [
        {
            'name': 'Python & Django Developers Global',
            'slug': 'python-django-developers',
            'creator': users['alex_dev'],
            'description': 'The premier community for Python engineers, Django architects, and API builders. Discuss best practices, ORM optimizations, async views and architecture.',
            'is_private': False,
        },
        {
            'name': 'Artificial Intelligence & LLM Engineers',
            'slug': 'ai-llm-engineers',
            'creator': users['sarah_ai'],
            'description': 'Exploring the frontier of deep learning, RAG pipelines, fine-tuning open source models and multi-agent coordination.',
            'is_private': False,
        },
        {
            'name': 'Search Engine Systems & Information Retrieval',
            'slug': 'search-engine-systems',
            'creator': users['admin'],
            'description': 'Deep dives into Apache Lucene, BM25 ranking, inverted indexes, web crawling and distributed vector search.',
            'is_private': False,
        },
        {
            'name': 'Product Design & UX Leaders',
            'slug': 'product-design-ux',
            'creator': users['michael_product'],
            'description': 'A collaborative hub for UI/UX innovators, design systems specialists, and product strategists.',
            'is_private': False,
        }
    ]

    groups = {}
    for gdata in groups_data:
        grp, _ = Group.objects.get_or_create(slug=gdata['slug'], defaults={
            'name': gdata['name'],
            'creator': gdata['creator'],
            'description': gdata['description'],
            'is_private': gdata['is_private']
        })
        for u in users.values():
            grp.members.add(u)
        groups[gdata['slug']] = grp

    # 4. Create Company Pages
    companies_data = [
        {
            'name': 'SmartSearch Innovations',
            'slug': 'smartsearch-innovations',
            'owner': users['admin'],
            'tagline': 'Intelligent, Private & Next-Generation Search Engine Ecosystem',
            'description': 'SmartSearch is an advanced federated search engine and knowledge network built on Apache Lucene and enterprise intelligence.',
            'industry': 'Search & Information Technology',
            'company_size': '200-500 employees',
            'website': 'http://localhost:8080/',
            'location': 'San Francisco, CA & Worldwide',
        },
        {
            'name': 'Apex AI Labs',
            'slug': 'apex-ai-labs',
            'owner': users['sarah_ai'],
            'tagline': 'Advancing Human-Centric Artificial General Intelligence',
            'description': 'Pioneering foundational cognitive research, multimodal understanding and high-precision language reasoning.',
            'industry': 'Artificial Intelligence & Research',
            'company_size': '50-200 employees',
            'website': 'https://apex-ai-labs.example.com',
            'location': 'London, UK',
        },
        {
            'name': 'CloudScale Systems',
            'slug': 'cloudscale-systems',
            'owner': users['alex_dev'],
            'tagline': 'High-Throughput Kubernetes & Cloud Native Infrastructure',
            'description': 'Empowering engineering teams worldwide to deploy, scale and observe distributed microservice workloads.',
            'industry': 'Cloud Computing & DevOps',
            'company_size': '100-250 employees',
            'website': 'https://cloudscale.example.com',
            'location': 'Bengaluru, India',
        }
    ]

    companies = {}
    for cdata in companies_data:
        comp, _ = CompanyPage.objects.get_or_create(slug=cdata['slug'], defaults={
            'name': cdata['name'],
            'owner': cdata['owner'],
            'tagline': cdata['tagline'],
            'description': cdata['description'],
            'industry': cdata['industry'],
            'company_size': cdata['company_size'],
            'website': cdata['website'],
            'location': cdata['location']
        })
        for u in users.values():
            comp.followers.add(u)
        companies[cdata['slug']] = comp

    # 5. Establish Connections & Follows
    # admin connected with alex_dev and sarah_ai
    Connection.objects.get_or_create(sender=users['admin'], receiver=users['alex_dev'], defaults={'status': 'accepted'})
    Connection.objects.get_or_create(sender=users['admin'], receiver=users['sarah_ai'], defaults={'status': 'accepted'})
    Connection.objects.get_or_create(sender=users['alex_dev'], receiver=users['sarah_ai'], defaults={'status': 'accepted'})
    Connection.objects.get_or_create(sender=users['michael_product'], receiver=users['admin'], defaults={'status': 'accepted'})
    # pending connection from emily to admin
    Connection.objects.get_or_create(sender=users['emily_frontend'], receiver=users['admin'], defaults={'status': 'pending'})

    # Follow relations
    for u in users.values():
        if u != users['admin']:
            Follow.objects.get_or_create(follower=u, following=users['admin'])
            Follow.objects.get_or_create(follower=users['admin'], following=u)

    # 6. Seed Posts & Discussions
    posts_data = [
        {
            'author': users['admin'],
            'content': "🚀 Exciting milestone! We have officially integrated our brand new Python Social Media Platform directly inside SmartSearch under Services → Social Media Platform.\n\nNow members can connect, share engineering insights, follow leading tech companies, and exchange real-time direct messages across the network. Welcome everyone!",
            'visibility': 'public',
            'link_url': 'http://localhost:8080/services/social-media',
            'likes_users': [users['alex_dev'], users['sarah_ai'], users['michael_product'], users['emily_frontend']],
            'comments': [
                (users['alex_dev'], "Incredible work! The responsiveness and clean UI feel super fluid."),
                (users['sarah_ai'], "Excited to see the AI communities and discussions take off here!"),
                (users['michael_product'], "Outstanding execution. A truly integrated social and search platform."),
            ]
        },
        {
            'author': users['sarah_ai'],
            'content': "🧠 Why Retrieval-Augmented Generation (RAG) is transforming enterprise search:\n\n1. Eliminates hallucinations by grounding LLM responses in real indexed documents.\n2. Provides verifiable citations and source links back to original articles.\n3. Enables real-time knowledge updates without expensive full-model retraining.\n\nWhat are your thoughts on hybrid dense + sparse retrieval?",
            'visibility': 'public',
            'group': groups['ai-llm-engineers'],
            'likes_users': [users['admin'], users['alex_dev']],
            'comments': [
                (users['admin'], "Combining Apache Lucene BM25 scoring with dense vector embeddings gives the best of both worlds in precision and semantic recall."),
                (users['alex_dev'], "Totally agreed! BM25 handles exact keyword IDs and technical jargon that dense embeddings sometimes miss."),
            ]
        },
        {
            'author': users['alex_dev'],
            'content': "⚡ Top 3 Django optimization tips for high-traffic social platforms:\n\n1. Always use select_related() and prefetch_related() to prevent N+1 query bottlenecks.\n2. Offload heavy notifications and email sending to asynchronous queues.\n3. Implement HTTP caching and database connection pooling with MySQL/Postgres.\n\nHappy coding everyone! 💻🐍",
            'visibility': 'public',
            'group': groups['python-django-developers'],
            'likes_users': [users['admin'], users['emily_frontend']],
            'comments': [
                (users['emily_frontend'], "Spot on Alex! Fast backend APIs make frontend rendering smooth and latency-free."),
            ]
        },
        {
            'author': users['michael_product'],
            'content': "Designing for simplicity is always harder than designing for complexity. Our core goal across SmartSearch Social is to make connecting with peers as natural and frictionless as possible. Feedback is always welcome!",
            'visibility': 'public',
            'likes_users': [users['admin'], users['sarah_ai']],
            'comments': []
        }
    ]

    for pdata in posts_data:
        p, created = Post.objects.get_or_create(
            author=pdata['author'],
            content=pdata['content'],
            defaults={
                'visibility': pdata.get('visibility', 'public'),
                'link_url': pdata.get('link_url', ''),
                'group': pdata.get('group', None),
            }
        )
        if created:
            for lk in pdata.get('likes_users', []):
                p.likes.add(lk)
            for c_author, c_text in pdata.get('comments', []):
                Comment.objects.create(post=p, author=c_author, content=c_text)

    # 7. Seed Sample Direct Messages
    Message.objects.get_or_create(
        sender=users['alex_dev'],
        receiver=users['admin'],
        content="Hi Admin! The new Social Media platform looks fantastic. Love the clean design.",
        defaults={'is_read': True}
    )
    Message.objects.get_or_create(
        sender=users['admin'],
        receiver=users['alex_dev'],
        content="Thanks Alex! Glad you like it. Let me know if you have any feature ideas for our developer groups.",
        defaults={'is_read': True}
    )
    Message.objects.get_or_create(
        sender=users['sarah_ai'],
        receiver=users['admin'],
        content="Hey! Just published a new discussion topic in the AI & LLM Engineers community. Check it out when you have a moment!",
        defaults={'is_read': False}
    )

    # 8. Seed Sample Notifications for Admin
    Notification.objects.get_or_create(
        recipient=users['admin'],
        sender=users['emily_frontend'],
        notification_type='connection_request',
        text="Emily Weber sent you a connection request.",
        defaults={'link': '/network/?tab=requests', 'is_read': False}
    )
    Notification.objects.get_or_create(
        recipient=users['admin'],
        sender=users['alex_dev'],
        notification_type='like',
        text="Alex Rivera liked your post.",
        defaults={'link': '/feed/', 'is_read': True}
    )

    print("Database seeding completed successfully!")

if __name__ == '__main__':
    run_seed()

